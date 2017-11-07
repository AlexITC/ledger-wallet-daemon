package co.ledger.wallet.daemon.services

import java.util.UUID
import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.models._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountsService @Inject()(defaultDaemonCache: DaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def accounts(user: User, poolName: String, walletName: String): Future[Seq[AccountView]] = {
    defaultDaemonCache.getAccounts(user.pubKey, poolName, walletName).flatMap { wallets =>
      Future.sequence(wallets.map { wallet => wallet.accountView })
    }
  }

  def account(accountIndex: Int, user: User, poolName: String, walletName: String): Future[Option[AccountView]] = {
    defaultDaemonCache.getAccount(accountIndex, user.pubKey, poolName, walletName).flatMap {
      case Some(account) => account.accountView.map(Option(_))
      case None => Future(None)
    }
  }

  def nextAccountCreationInfo(user: User, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivationView] = {
    defaultDaemonCache.getNextAccountCreationInfo(user.pubKey, poolName, walletName, accountIndex).map(_.view)
  }

  def accountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, queryParams: OperationQueryParams): Future[PackedOperationsView] = {
    if(queryParams.next.isEmpty && queryParams.previous.isEmpty) {
      // new request
      info(LogMsgMaker.newInstance("Retrieve latest operations").toString())
      defaultDaemonCache.getAccountOperations(user, accountIndex, poolName, walletName, queryParams.batch, queryParams.fullOp)
    } else if (queryParams.next.isDefined) {
      // next has more priority, using database batch instead queryParams.batch
      info(LogMsgMaker.newInstance("Retrieve next batch operation").toString())
      defaultDaemonCache.getNextBatchAccountOperations(user, accountIndex, poolName, walletName, queryParams.next.get, queryParams.fullOp)
    } else {
      info(LogMsgMaker.newInstance("Retrieve previous operations").toString())
      defaultDaemonCache.getPreviousBatchAccountOperations(user, accountIndex, poolName, walletName, queryParams.previous.get, queryParams.fullOp)
    }
  }

//  def accountOperation(user: User, uid: String, accountIndex: Int, poolName: String, walletName: String): Future[Option[OperationView]] = {
//    defaultDaemonCache.getOperation(user, uid, accountIndex, poolName, walletName).map ()
//  }

  def createAccount(accountCreationBody: AccountDerivationView, user: User, poolName: String, walletName: String): Future[AccountView] = {
    defaultDaemonCache.createAccount(accountCreationBody, user, poolName, walletName).flatMap(_.accountView)
  }
}

case class OperationQueryParams(previous: Option[UUID], next: Option[UUID], batch: Int = 20, fullOp: Int = 0)