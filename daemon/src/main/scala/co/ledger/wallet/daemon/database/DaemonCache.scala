package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.wallet.daemon.models._

import scala.concurrent.Future

trait DaemonCache {

  // ************** account *************
  def createAccount(accountDerivation: AccountDerivationView, user: UserDto, poolName: String, walletName: String): Future[AccountView]

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[AccountView]]

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[AccountView]

  def getAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, batch: Int, fullOp: Int): Future[PackedOperationsView]

  def getNextBatchAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, next: UUID, fullOp: Int): Future[PackedOperationsView]

  def getPreviousBatchAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, previous: UUID, fullOp: Int): Future[PackedOperationsView]

  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivationView]

  // ************** currency ************
  def getCurrency(currencyName: String, poolName: String): Future[CurrencyView]

  def getCurrencies(poolName: String): Future[Seq[CurrencyView]]

  // ************** wallet *************
  def createWallet(walletName: String, currencyName: String, poolName: String, user: UserDto): Future[WalletView]

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[WalletsViewWithCount]

  def getWallet(walletName: String, poolName: String, pubKey: String): Future[WalletView]

  // ************** wallet pool *************
  def createWalletPool(user: UserDto, poolName: String, configuration: String): Future[WalletPoolView]

  def getWalletPool(pubKey: String, poolName: String): Future[WalletPoolView]

  def getWalletPools(pubKey: String): Future[Seq[WalletPoolView]]

  def deleteWalletPool(user: UserDto, poolName: String): Future[Unit]

  def syncOperations(): Future[Seq[SynchronizationResult]]

  //**************** user ***************
  def getUserDirectlyFromDB(pubKey: Array[Byte]): Future[Option[UserDto]]

  def createUser(user: UserDto): Future[Long]

}
