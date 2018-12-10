package co.ledger.wallet.daemon.services

import java.util.UUID

import cats.implicits._
import co.ledger.core
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DaemonCache
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
class AccountsService @Inject()(daemonCache: DaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def accounts(walletInfo: WalletInfo): Future[Seq[AccountView]] = {
    daemonCache.withWallet(walletInfo) { wallet =>
      wallet.accounts.flatMap { as =>
        as.toList.map(a => a.accountView(walletInfo.walletName, wallet.getCurrency.currencyView)).sequence[Future, AccountView]
      }
    }
  }

  def account(accountInfo: AccountInfo): Future[Option[AccountView]] = {
    daemonCache.withWallet(accountInfo.walletInfo) { wallet =>
      wallet.account(accountInfo.accountIndex).flatMap(ao =>
        ao.map(_.accountView(accountInfo.walletName, wallet.getCurrency.currencyView)).sequence)
    }
  }

  def synchronizeAccount(accountInfo: AccountInfo): Future[Seq[SynchronizationResult]] = {
    daemonCache.syncOperations(accountInfo)
  }

  def getAccount(accountInfo: AccountInfo): Future[Option[core.Account]] = {
    daemonCache.getAccount(accountInfo: AccountInfo)
  }

  def getBalance(contract: Option[String], accountInfo: AccountInfo): Future[Long] =
    daemonCache.withAccount(accountInfo)(a => contract match {
      case Some(c) => a.erc20Balance(c).liftTo[Future]
      case None => a.balance
    })

  def getERC20Operations(contract: String, accountInfo: AccountInfo): Future[List[ERC20OperationView]] =
    daemonCache.withAccount(accountInfo)(_.erc20Operation(contract).map(_.map(ERC20OperationView(_))).liftTo[Future])

  def accountFreshAddresses(accountInfo: AccountInfo): Future[Seq[FreshAddressView]] = {
    daemonCache.getFreshAddresses(accountInfo)
  }

  def accountDerivationPath(accountInfo: AccountInfo): Future[String] = {
    daemonCache.getDerivationPath(accountInfo)
  }

  def nextAccountCreationInfo(accountIndex: Option[Int], walletInfo: WalletInfo): Future[AccountDerivationView] = {
    daemonCache.getNextAccountCreationInfo(accountIndex, walletInfo).map(_.view)
  }

  def nextExtendedAccountCreationInfo(accountIndex: Option[Int], walletInfo: WalletInfo): Future[AccountExtendedDerivationView] = {
    daemonCache.getNextExtendedAccountCreationInfo(accountIndex, walletInfo).map(_.view)
  }

  def accountOperations(queryParams: OperationQueryParams, accountInfo: AccountInfo): Future[PackedOperationsView] = {
    (queryParams.next, queryParams.previous) match {
      case (Some(n), _) =>
        // next has more priority, using database batch instead queryParams.batch
        info(LogMsgMaker.newInstance("Retrieve next batch operation").toString())
        daemonCache.getNextBatchAccountOperations(n, queryParams.fullOp, accountInfo)
      case (_, Some(p)) =>
        info(LogMsgMaker.newInstance("Retrieve previous operations").toString())
        daemonCache.getPreviousBatchAccountOperations(p, queryParams.fullOp, accountInfo)
      case _ =>
        // new request
        info(LogMsgMaker.newInstance("Retrieve latest operations").toString())
        daemonCache.getAccountOperations(queryParams.batch, queryParams.fullOp, accountInfo)
    }
  }

  def firstOperation(accountInfo: AccountInfo): Future[Option[OperationView]] = {
    daemonCache.withAccountAndWallet(accountInfo) {
      case (account, wallet) =>
        account.firstOperation flatMap {
          case None => Future(None)
          case Some(o) => Operations.getView(o, wallet, account).map(Some(_))
        }
    }
  }

  def accountOperation(uid: String, fullOp: Int, accountInfo: AccountInfo): Future[Option[OperationView]] =
    daemonCache.getAccountOperation(uid, fullOp, accountInfo)

  def createAccount(accountCreationBody: AccountDerivationView, walletInfo: WalletInfo): Future[AccountView] =
    daemonCache.withWallet(walletInfo) {
      w => daemonCache.createAccount(accountCreationBody, w).flatMap(_.accountView(walletInfo.walletName, w.getCurrency.currencyView))
    }

  def createAccountWithExtendedInfo(derivations: AccountExtendedDerivationView, walletInfo: WalletInfo): Future[AccountView] =
    daemonCache.withWallet(walletInfo) {
      w => daemonCache.createAccount(derivations, w).flatMap(_.accountView(walletInfo.walletName, w.getCurrency.currencyView))
    }

}

case class OperationQueryParams(previous: Option[UUID], next: Option[UUID], batch: Int, fullOp: Int)