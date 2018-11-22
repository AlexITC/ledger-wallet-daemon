package co.ledger.wallet.daemon.services

import co.ledger.core.{Currency, WalletType}
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.TransactionsController._
import co.ledger.wallet.daemon.database.DefaultDaemonCache
import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, WalletNotFoundException}
import co.ledger.wallet.daemon.models.Account._
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import co.ledger.wallet.daemon.utils.Utils._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.internal.marshalling.MessageBodyManager
import javax.inject.{Inject, Singleton}

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
class TransactionsService @Inject()(defaultDaemonCache: DefaultDaemonCache, messageBodyManager: MessageBodyManager) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  def createTransaction(request: Request, accountInfo: AccountInfo): Future[TransactionView] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(accountInfo.walletName, accountInfo.poolName, accountInfo.user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(accountInfo.walletName))
      transactionInfoEither = wallet.getWalletType match {
        case WalletType.BITCOIN => Right(messageBodyManager.read[CreateBTCTransactionRequest](request))
        case WalletType.ETHEREUM => Right(messageBodyManager.read[CreateETHTransactionRequest](request))
        case w => Left(CurrencyNotFoundException(w.name()))
      }
      tv <- transactionInfoEither match {
        case Right(transactionInfo) => createTransaction(transactionInfo.transactionInfo, accountInfo, wallet.getCurrency)
        case Left(t) => Future.failed(t)
      }
    } yield tv
  }

  def createTransaction(transactionInfo: TransactionInfo, accountInfo: AccountInfo, currency: Currency): Future[TransactionView] = {
    for {
      tv <- defaultDaemonCache.getHardAccount(accountInfo.user, accountInfo.poolName, accountInfo.walletName, accountInfo.accountIndex)
        .flatMap { case (_, _, account) =>
          account.createTransaction(transactionInfo, currency)
        }
    } yield tv
  }

  def broadcastTransaction(request: Request, accountInfo: AccountInfo): Future[String] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(accountInfo.walletName, accountInfo.poolName, accountInfo.user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(accountInfo.walletName))
      currentHeight <- wallet.lastBlockHeight
      account <- wallet.account(accountInfo.accountIndex)
      r <- wallet.getWalletType match {
        case WalletType.BITCOIN =>
          val req = messageBodyManager.read[PublishBTCTransactionRequest](request)
          account.get.broadcastBTCTransaction(req.rawTx, req.pairedSignatures, currentHeight, wallet.getCurrency)
        case WalletType.ETHEREUM =>
          val req = messageBodyManager.read[PublishETHTransactionRequest](request)
          account.get.broadcastETHTransaction(req.hexTx, req.hexSig, wallet.getCurrency)
        case w => Future.failed(CurrencyNotFoundException(w.name()))
      }
    } yield r
  }

  def signTransaction(rawTx: Array[Byte], pairedSignatures: Seq[(Array[Byte],Array[Byte])], accountInfo: AccountInfo): Future[String] = {
    for {
      walletOption <- defaultDaemonCache.getWallet(accountInfo.walletName, accountInfo.poolName, accountInfo.user.pubKey)
      wallet <- walletOption.toFuture(WalletNotFoundException(accountInfo.walletName))
      currentHeight <- wallet.lastBlockHeight
      r <- defaultDaemonCache.getHardAccount(accountInfo.user, accountInfo.poolName, accountInfo.walletName, accountInfo.accountIndex)
        .flatMap { case (_, _, account) =>
          account.broadcastBTCTransaction(rawTx, pairedSignatures, currentHeight, wallet.getCurrency)
        }
    } yield r
  }
}
