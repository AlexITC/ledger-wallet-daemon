package co.ledger.wallet.daemon.services

import java.util.UUID

import cats.implicits._
import co.ledger.core
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Currency._
import co.ledger.wallet.daemon.models.Operations.{OperationView, PackedOperationsView}
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.models.coins.ERC20OperationView
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountsService @Inject()(defaultDaemonCache: DaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def accounts(user: User, poolName: String, walletName: String): Future[Seq[AccountView]] = {
    defaultDaemonCache.withWallet(walletName, poolName, user.pubKey) { wallet =>
      wallet.accounts.flatMap { as =>
        as.toList.map(a => a.accountView(walletName, wallet.getCurrency.currencyView)).sequence[Future, AccountView]
      }
    }
  }

  def account(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Option[AccountView]] = {
    defaultDaemonCache.withWallet(walletName, poolName, user.pubKey) { wallet =>
      wallet.account(accountIndex).flatMap(ao =>
        ao.map(_.accountView(walletName, wallet.getCurrency.currencyView)).sequence)
    }
  }

  def synchronizeAccount(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Seq[SynchronizationResult]] = {
    defaultDaemonCache.syncOperations(user.pubKey, poolName, walletName, accountIndex)
  }

  def getAccount(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Option[core.Account]] = {
    defaultDaemonCache.getAccount(accountIndex, user.pubKey, poolName, walletName)
  }

  def getBalance(accountIndex: Int, user: User, poolName: String, walletName:String, contract: Option[String]): Future[Long] =
    defaultDaemonCache.withAccount(accountIndex, walletName, poolName, user.pubKey)(a => contract match {
      case Some(c) => a.erc20Balance(c).liftTo[Future]
      case None => a.balance
    })

  def getERC20Operations(accountIndex: Int, user: User, poolName: String, walletName: String, contract: String): Future[List[ERC20OperationView]] =
    defaultDaemonCache.withAccount(accountIndex, walletName, poolName, user.pubKey)(_.erc20Operation(contract).map(_.map(ERC20OperationView(_))).liftTo[Future])

  def accountFreshAddresses(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Seq[FreshAddressView]] = {
    defaultDaemonCache.getFreshAddresses(accountIndex, user.pubKey, poolName, walletName)
  }

  def accountDerivationPath(accountIndex: Int, user: User, poolName: String, walletName: String): Future[String] = {
    defaultDaemonCache.getDerivationPath(accountIndex, user.pubKey, poolName, walletName)
  }

  def nextAccountCreationInfo(user: User, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivationView] = {
    defaultDaemonCache.getNextAccountCreationInfo(user.pubKey, poolName, walletName, accountIndex).map(_.view)
  }

  def nextExtendedAccountCreationInfo(user: User, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountExtendedDerivationView] = {
    defaultDaemonCache.getNextExtendedAccountCreationInfo(user.pubKey, poolName, walletName, accountIndex).map(_.view)
  }

  def accountOperations(
                         user: User,
                         accountIndex: Int,
                         poolName: String,
                         walletName: String,
                         queryParams: OperationQueryParams): Future[PackedOperationsView] = {
    (queryParams.next, queryParams.previous) match {
      case (Some(n), _) =>
        // next has more priority, using database batch instead queryParams.batch
        info(LogMsgMaker.newInstance("Retrieve next batch operation").toString())
        defaultDaemonCache.getNextBatchAccountOperations(user, accountIndex, poolName, walletName, n, queryParams.fullOp)
      case (_, Some(p)) =>
        info(LogMsgMaker.newInstance("Retrieve previous operations").toString())
        defaultDaemonCache.getPreviousBatchAccountOperations(user, accountIndex, poolName, walletName, p, queryParams.fullOp)
      case _ =>
        // new request
        info(LogMsgMaker.newInstance("Retrieve latest operations").toString())
        defaultDaemonCache.getAccountOperations(user, accountIndex, poolName, walletName, queryParams.batch, queryParams.fullOp)
    }
  }

  def firstOperation(user: User, accountIndex: Int, poolName: String, walletName: String): Future[Option[OperationView]] = {
    defaultDaemonCache.withAccountAndWallet(accountIndex, walletName, poolName, user.pubKey) {
      case (account, wallet) =>
        account.firstOperation flatMap {
          case None => Future(None)
          case Some(o) => Operations.getView(o, wallet, account).map(Some(_))
        }
    }
  }

  def accountOperation(user: User, uid: String, accountIndex: Int, poolName: String, walletName: String, fullOp: Int): Future[Option[OperationView]] =
    defaultDaemonCache.getAccountOperation(user.pubKey, uid, accountIndex, poolName, walletName, fullOp)

  def createAccount(accountCreationBody: AccountDerivationView, user: User, poolName: String, walletName: String): Future[AccountView] =
    defaultDaemonCache.withWallet(walletName, poolName, user.pubKey) {
      w => defaultDaemonCache.createAccount(accountCreationBody, w).flatMap(_.accountView(walletName, w.getCurrency.currencyView))
    }

  def createAccountWithExtendedInfo(derivations: AccountExtendedDerivationView, user: User, poolName: String, walletName: String): Future[AccountView] =
    defaultDaemonCache.withWallet(walletName, poolName, user.pubKey) {
      w => defaultDaemonCache.createAccount(derivations, w).flatMap(_.accountView(walletName, w.getCurrency.currencyView))
    }

}

case class OperationQueryParams(previous: Option[UUID], next: Option[UUID], batch: Int, fullOp: Int)