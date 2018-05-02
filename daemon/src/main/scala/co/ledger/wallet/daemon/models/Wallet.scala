package co.ledger.wallet.daemon.models

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import co.ledger.core
import co.ledger.core.implicits
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.exceptions.InvalidArgumentException
import co.ledger.wallet.daemon.models.Account.{Account, Derivation}
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.{AsArrayList, HexUtils}
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class Wallet(private val coreW: core.Wallet, private val pool: Pool) extends Logging with GenCache {
  private[this] val self = this

  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  implicit def asArrayList[T](input: Seq[T]): AsArrayList[T] = new AsArrayList[T](input)
  private[this] val cachedAccounts: Cache[Int, Account] = newCache(initialCapacity = INITIAL_ACCOUNT_CAP_PER_WALLET)
  private[this] val accountLen: AtomicInteger = new AtomicInteger(0)
  private val configuration: Map[String, Any] = Map[String, Any]()
  private[this] val currentBlockHeight: AtomicLong = new AtomicLong(-1)

  val name: String = coreW.getName
  val currency: Currency = Currency.newInstance(coreW.getCurrency)

  def convertAmount(amount: Long): core.Amount = core.Amount.fromLong(coreW.getCurrency, amount)

  def lastBlockHeight: Future[Long] =
    if (currentBlockHeight.get() < 0) {
      coreW.getLastBlock().map { lastBlock => updateBlockHeight(lastBlock.getHeight); currentBlockHeight.get() }
    } else {
      Future.successful(currentBlockHeight.get())
    }

  def updateBlockHeight(newHeight: Long): Unit = {
    val updated = currentBlockHeight.updateAndGet(n => Math.max(n, newHeight))
    debug(LogMsgMaker.newInstance("Update block height").append("to", updated).append("at", newHeight).append("wallet", name).toString())
  }

  def walletView: Future[WalletView] = {
    getBalance.map { b => WalletView(name, accountLen.get(), b, currency.currencyView, configuration) }
  }

  def account(index: Int): Future[Option[Account]] = {
    cachedAccounts.get(index) match {
      case Some(account) => Future.successful(Option(account))
      case None => toCacheAndStartListen(accountLen.get()).map { _ => cachedAccounts.get(index)}
    }
  }

  def accountCreationInfo(index: Option[Int]): Future[Derivation] = {
    (index match {
      case Some(i) => coreW.getAccountCreationInfo(i)
      case None => coreW.getNextAccountCreationInfo()
    }).map { info => Account.newDerivation(info) }
  }


  def accounts(): Future[Seq[Account]] = {
    coreW.getAccountCount().flatMap { count =>
      if(count == accountLen.get()) { Future.successful(cachedAccounts.values.toSeq) }
      else { toCacheAndStartListen(accountLen.get()).map { _ => cachedAccounts.values.toSeq }}
    }
  }

  def addAccountIfNotExit(accountDerivations: AccountDerivationView): Future[Account] = {
    val accountCreationInfo = new core.AccountCreationInfo(
      accountDerivations.accountIndex,
      (for (derivationResult <- accountDerivations.derivations) yield derivationResult.owner).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield derivationResult.path).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield HexUtils.valueOf(derivationResult.pubKey.get)).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield HexUtils.valueOf(derivationResult.chainCode.get)).asArrayList
    )
    accountCreationInfo.getOwners.asScala.foreach(o => println(s"Owner: $o"))
    coreW.newAccountWithInfo(accountCreationInfo).map { coreA =>
      info(LogMsgMaker.newInstance("Account created").append("index", coreA.getIndex).append("wallet_name", name).toString())
      toCacheAndStartListen(accountLen.get()).map { _ =>
        val account = cachedAccounts(coreA.getIndex)
        account.sync(pool.name)
        account
      }
    }.recover {
      case e: implicits.InvalidArgumentException => Future.failed(InvalidArgumentException(e.getMessage))
      case _: implicits.AccountAlreadyExistsException =>
        warn(LogMsgMaker.newInstance("Account already exist").append("index", accountDerivations.accountIndex).append("wallet_name", name).toString())
        if(cachedAccounts.contains(accountDerivations.accountIndex)) { Future.successful(cachedAccounts(accountDerivations.accountIndex)) }
        else { toCacheAndStartListen(accountLen.get()).map { _ => cachedAccounts(accountDerivations.accountIndex) }}
    }.flatten
  }

  def syncAccounts(poolName: String): Future[Seq[SynchronizationResult]] = {
    accounts().flatMap { accounts =>
      Future.sequence(accounts.map { account => account.sync(poolName)})
    }
  }

  def startCacheAndRealTimeObserver(): Future[Unit] = {
    toCacheAndStartListen(accountLen.get())
  }

  def stopRealTimeObserver(): Unit = {
    debug(LogMsgMaker.newInstance("Stop real time observer").append("wallet", self).toString())
    cachedAccounts.values.toSeq.foreach { account => account.stopRealTimeObserver() }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Wallet => that.isInstanceOf[Wallet] && self.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    self.name.hashCode + self.currency.hashCode()
  }

  override def toString: String = s"Wallet(name: $name, currency: ${currency.name})"

  private def toCacheAndStartListen(offset: Int): Future[Unit] = {
    if (offset < accountLen.get()) { Future { info(s"Wallet $name cache was already updated")}}
    else {
      coreW.getAccountCount().flatMap { count =>
        if (count == offset) {
          Future(info(s"Wallet $name cache is up to date"))
        } else if (count < offset) {
          Future(warn(s"Offset should be less than count, possible race condition"))
        } else {
          coreW.getAccounts(offset, count).map { coreAs =>
            coreAs.asScala.foreach { coreA =>
              cachedAccounts.put(coreA.getIndex, Account.newInstance(coreA, self))
              debug(s"Add ${cachedAccounts(coreA.getIndex)} to $self cache")
              cachedAccounts(coreA.getIndex).startRealTimeObserver()
            }
            accountLen.updateAndGet(n => Math.max(n, count))
          }
        }
      }
    }
  }

  private def getBalance: Future[Long] = {
    accounts().flatMap { as =>
      Future.sequence( for (a <- as) yield a.balance ).map { b => b.sum }
    }
  }

}

object Wallet {
  def newInstance(coreW: core.Wallet, pool: Pool): Wallet = {
    new Wallet(coreW, pool)
  }
}

case class WalletView(
                       @JsonProperty("name") name: String,
                       @JsonProperty("account_count") accountCount: Int,
                       @JsonProperty("balance") balance: Long,
                       @JsonProperty("currency") currency: CurrencyView,
                       @JsonProperty("configuration") configuration: Map[String, Any]
                     )

case class WalletsViewWithCount(count: Int, wallets: Seq[WalletView])