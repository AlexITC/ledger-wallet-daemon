package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.TransactionsController.{AccountInfo, TransactionInfo}
import co.ledger.wallet.daemon.database.DefaultDaemonCache
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView

import scala.concurrent.{ExecutionContext, Future}

/**
  * Business logic for transaction operations.
  *
  * User: Ting Tu
  * Date: 24-04-2018
  * Time: 14:14
  *
  */
@Singleton
class TransactionsService @Inject()(defaultDaemonCache: DefaultDaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def createTransaction(transactionInfo: TransactionInfo, accountInfo: AccountInfo): Future[TransactionView] = {
    defaultDaemonCache.getHardAccount(accountInfo.user.pubKey, accountInfo.poolName, accountInfo.walletName, accountInfo.index)
      .flatMap { account =>
        account.createTransaction(transactionInfo)
    }
  }
}
