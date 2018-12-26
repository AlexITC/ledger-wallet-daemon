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

  /**
    * Method to synchronize account operations from public resources. The method may take a while
    * to finish. This method onlly synchronize a single account.
    *
    * @return a Future of sequence of result of synchronization.
    */
  def synchronizeAccount(accountInfo: AccountInfo): Future[Seq[SynchronizationResult]] =
    daemonCache.withAccount(accountInfo)(_.sync(accountInfo.poolName, accountInfo.walletName).map(Seq(_)))

  def getAccount(accountInfo: AccountInfo): Future[Option[core.Account]] = {
    daemonCache.getAccount(accountInfo: AccountInfo)
  }

  def getBalance(contract: Option[String], accountInfo: AccountInfo): Future[Long] =
    daemonCache.withAccount(accountInfo)(a => contract match {
      case Some(c) => a.erc20Balance(c).liftTo[Future]
      case None => a.balance
    })

  def getERC20Operations(tokenAccountInfo: TokenAccountInfo): Future[List[OperationView]] =
    daemonCache.withAccountAndWallet(tokenAccountInfo.accountInfo){
      case (account, wallet) =>
      account.erc20Operations(tokenAccountInfo.tokenAddress).flatMap(_.traverse(Operations.getView(_, wallet, account)))
    }

  def getERC20Operations(accountInfo: AccountInfo): Future[List[OperationView]] =
    daemonCache.withAccountAndWallet(accountInfo){
      case (account, wallet) =>
      account.erc20Operations.flatMap(_.traverse(Operations.getView(_, wallet, account)))
    }

  def getTokenAccounts(accountInfo: AccountInfo): Future[List[ERC20AccountView]] =
    daemonCache.withAccount(accountInfo)(_.erc20Accounts.map(_.map(ERC20AccountView(_))).liftTo[Future])

  def getTokenAccount(tokenAccountInfo: TokenAccountInfo): Future[ERC20AccountView] =
    daemonCache.withAccount(tokenAccountInfo.accountInfo)(_.erc20Account(tokenAccountInfo.tokenAddress).map(ERC20AccountView(_)).liftTo[Future])

  def accountFreshAddresses(accountInfo: AccountInfo): Future[Seq[FreshAddressView]] = {
    daemonCache.getFreshAddresses(accountInfo)
  }

  /**
    * Getter for fresh addresses of specified account.
    *
    * @param accountIndex the unique index of specified account.
    * @param pubKey       the publick key of instance of `co.ledger.wallet.daemon.DefaultDaemonCache.User`.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @return a Future of a sequence of instances of `co.ledger.wallet.daemon.models.Account`.
    */
  def accountDerivationPath(accountInfo: AccountInfo): Future[String] =
    daemonCache.withWallet(accountInfo.walletInfo)(_.accountDerivationPathInfo(accountInfo.accountIndex))

  /**
    * Getter of information for next account creation.
    *
    * @param pubKey       the public key of user.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @param accountIndex the unique index of the account. If `None`, a default index will be created. If the
    *                     specified index already exists in core library, an error will occur. `None` is recommended.
    * @return a Future of `co.ledger.wallet.daemon.models.Derivation` instance.
    */
  def nextAccountCreationInfo(accountIndex: Option[Int], walletInfo: WalletInfo): Future[AccountDerivationView] =
    daemonCache.withWallet(walletInfo)(_.accountCreationInfo(accountIndex)).map(_.view)

  /**
    * Getter of information for next account creation with extended keys.
    *
    * @param pubKey       the public key of user.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @param accountIndex the unique index of the account. If `None`, a default index will be created. If the
    *                     specified index already exists in core library, an error will occur. `None` is recommended.
    * @return a Future of `co.ledger.wallet.daemon.models.Derivation` instance.
    */
  def nextExtendedAccountCreationInfo(accountIndex: Option[Int], walletInfo: WalletInfo)(implicit ec: ExecutionContext): Future[AccountExtendedDerivationView] =
    daemonCache.withWallet(walletInfo)(_.accountExtendedCreation(accountIndex)).map(_.view)

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

  /**
    * Getter for account operation instance with specified uid.
    *
    * @param user         the user who can access the account.
    * @param uid          the unique identifier of operation defined by core lib.
    * @param accountIndex the unique account index.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @param fullOp       the flag specifying the query result details. If greater than zero, detailed operations,
    *                     including transaction information, will be returned.
    * @return a Future of optional `co.ledger.wallet.daemon.models.Operation` instance.
    */
  def accountOperation(uid: String, fullOp: Int, accountInfo: AccountInfo): Future[Option[OperationView]] =
    daemonCache.withAccountAndWallet(accountInfo) {
      case (account, wallet) =>
        for {
          operationOpt <- account.operation(uid, fullOp)
          op <- operationOpt match {
            case None => Future.successful(None)
            case Some(op) => Operations.getView(op, wallet, account).map(Some(_))
          }
        } yield op
    }

  def createAccount(accountCreationBody: AccountDerivationView, walletInfo: WalletInfo): Future[AccountView] =
    daemonCache.withWallet(walletInfo) {
      w => w.addAccountIfNotExist(accountCreationBody).flatMap(_.accountView(walletInfo.walletName, w.getCurrency.currencyView))
    }

  def createAccountWithExtendedInfo(derivations: AccountExtendedDerivationView, walletInfo: WalletInfo): Future[AccountView] =
    daemonCache.withWallet(walletInfo) {
      w => w.addAccountIfNotExist(derivations).flatMap(_.accountView(walletInfo.walletName, w.getCurrency.currencyView))
    }

}

case class OperationQueryParams(previous: Option[UUID], next: Option[UUID], batch: Int, fullOp: Int)