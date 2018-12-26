package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.core.{Account, Currency, Wallet}
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.exceptions.{AccountNotFoundException, UserNotFoundException, WalletNotFoundException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.models.Account.{Derivation, ExtendedDerivation}
import co.ledger.wallet.daemon.models.Operations.{OperationView, PackedOperationsView}
import co.ledger.wallet.daemon.models.Wallet._
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.models.Account._

import scala.concurrent.{ExecutionContext, Future}

trait DaemonCache {



  /**
    * Getter of account instance with specified parameters.
    *
    * @param accountIndex the unique index of specified instance.
    * @param pubKey       the public key of instance of `co.ledger.wallet.daemon.DefaultDaemonCache.User`.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @return a Future of an Option of the instance of `co.ledger.wallet.daemon.models.Account`.
    */
  def getAccount(accountInfo: AccountInfo)(implicit ec: ExecutionContext): Future[Option[Account]] =
    withWallet(accountInfo.walletInfo)(_.account(accountInfo.accountIndex))

  def withAccount[T](accountInfo: AccountInfo)(f: Account => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withWallet(accountInfo.walletInfo)(w => withAccount(accountInfo.accountIndex, w)(f))

  def withAccount[T](accountIndex: Int, wallet: Wallet)(f: Account => Future[T])(implicit ec: ExecutionContext): Future[T] =
    wallet.account(accountIndex).flatMap {
      case Some(account) => f(account)
      case None => Future.failed(AccountNotFoundException(accountIndex))
    }

  def withAccountAndWallet[T](accountInfo: AccountInfo)(f: (Account, Wallet) => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withAccountAndWalletAndPool(accountInfo){
      case (account, wallet, _) => f(account, wallet)
    }

  def withAccountAndWalletAndPool[T](accountInfo: AccountInfo)(f: (Account, Wallet, Pool) => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withWalletPool(accountInfo.walletInfo.poolInfo) { pool =>
      withWallet(accountInfo.walletInfo) { wallet =>
        withAccount(accountInfo.accountIndex, wallet) { account =>
          f(account, wallet, pool)
        }
      }
    }

  /**
    * Getter for fresh addresses of specified account.
    *
    * @param accountIndex the unique index of specified account.
    * @param user         the user who can access the account.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @return a Future of a sequence of instances of `co.ledger.wallet.daemon.models.Account`.
    */
  def getFreshAddresses(accountInfo: AccountInfo)(implicit ec: ExecutionContext): Future[Seq[FreshAddressView]] =
    withAccount(accountInfo)(_.freshAddresses).map(_.map(addr => FreshAddressView(addr.toString, addr.getDerivationPath)))

  /**
    * Getter of account operations batch instances with specified parameters.
    *
    * @param user         the user who can access the account.
    * @param accountIndex the unique index of specified instance.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @param batch        the operations count that need to be queried.
    * @param fullOp       the flag specifying the query result details. If greater than zero, detailed operations,
    *                     including transaction information, will be returned.
    * @return a Future of `co.ledger.wallet.daemon.models.PackedOperationsView` instance.
    */
  def getAccountOperations(batch: Int, fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView]


  /**
    * Getter of account operations batch instances with specified parameters.
    *
    * @param user         the user who can access the account.
    * @param accountIndex the unique index of specified instance.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @param next         the UUID indicating the offset of returning batch.
    * @param fullOp       the flag specifying the query result details. If greater than zero, detailed operations,
    *                     including transaction information, will be returned.
    * @return a Future of `co.ledger.wallet.daemon.models.PackedOperationView` instance.
    */
  def getNextBatchAccountOperations(next: UUID, fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView]

  /**
    * Getter of account operations batch instances with specified parameters.
    *
    * @param user         the user who can access the account.
    * @param accountIndex the unique index of specified account instance.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @param previous     the UUID indicating the offset of returning batch. The batch should be already requested
    *                     by another transaction.
    * @param fullOp       the flag specifying the query result details. If greater than zero, detailed operations,
    *                     including transaction information, will be returned.
    * @return a Future of `co.ledger.wallet.daemon.models.PackedOperationView` instance.
    */
  def getPreviousBatchAccountOperations(previous: UUID,
                                         fullOp: Int, accountInfo: AccountInfo): Future[PackedOperationsView]



  // ************** currency ************
  /**
    * Getter of `co.ledger.wallet.daemon.models.Currency` instance.
    *
    * @param currencyName the name of specified currency. Name is predefined by core library and is the
    *                     identifier of currencies.
    * @param poolName     the name of wallet pool for this currency.
    * @param pubKey       the public key of user.
    * @return a Future of `co.ledger.wallet.daemon.models.Currency` Option.
    */
  def getCurrency(currencyName: String, poolInfo: PoolInfo)(implicit ec: ExecutionContext): Future[Option[Currency]] =
    withWalletPool(poolInfo)(_.currency(currencyName))

  /**
    * Getter of `co.ledger.wallet.daemon.models.Currency` instances sequence.
    *
    * @param poolName the name of wallet pool of this currency.
    * @param pubKey   the public key of user.
    * @return a Future of sequence of `co.ledger.wallet.daemon.models.Currency` instances.
    */
  def getCurrencies(poolInfo: PoolInfo)(implicit ec: ExecutionContext): Future[Seq[Currency]] =
    withWalletPool(poolInfo)(_.currencies())

  // ************** wallet *************
  /**
    * Method to create a wallet instance.
    *
    * @param walletName   the name of this wallet.
    * @param currencyName the name of currency of this wallet.
    * @param poolName     the name of wallet pool contains the wallet.
    * @param user         the user who can access the wallet.
    * @return a Future of `co.ledger.wallet.daemon.models.Wallet` instance created.
    */
  def createWallet(currencyName: String, walletInfo: WalletInfo)(implicit ec: ExecutionContext): Future[Wallet] = {
    withWalletPool(walletInfo.poolInfo)(_.addWalletIfNotExist(walletInfo.walletName, currencyName))
  }

  /**
    * Getter of sequence of `co.ledger.wallet.daemon.models.Wallet` instances.
    *
    * @param offset   the offset of the returned wallet sequence.
    * @param batch    the batch size of the returned wallet sequence.
    * @param poolName the name of wallet pool the wallets belong to.
    * @param pubKey   the public key of the user.
    * @return a Future of a tuple containing the total wallets count and required sequence of wallets.
    */
  def getWallets(offset: Int, batch: Int, poolInfo: PoolInfo)(implicit ec: ExecutionContext): Future[(Int, Seq[Wallet])] = {
    withWalletPool(poolInfo)(_.wallets(offset, batch))
  }

  /**
    * Getter of instance of `co.ledger.wallet.daemon.models.Wallet`.
    *
    * @param walletName the name of specified wallet.
    * @param poolName   the name of the pool the wallet belongs to.
    * @param pubKey     the public key of the user.
    * @return a Future of `co.ledger.wallet.daemon.models.Wallet` instance Option.
    */
  def getWallet(walletInfo: WalletInfo)(implicit ec: ExecutionContext): Future[Option[Wallet]] = {
    withWalletPool(walletInfo.poolInfo)(_.wallet(walletInfo.walletName))
  }

  def withWallet[T](walletInfo: WalletInfo)(f: Wallet => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withWalletPool(walletInfo.poolInfo)(p => withWallet(walletInfo.walletName, p)(f))

  def withWallet[T](walletName: String, pool: Pool)(f: Wallet => Future[T])(implicit ec: ExecutionContext): Future[T] =
    pool.wallet(walletName).flatMap {
      case Some(w) => f(w)
      case None => Future.failed(WalletNotFoundException(walletName))
    }

  def withWalletAndPool[T](walletInfo: WalletInfo)(f: (Wallet, Pool) => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withWalletPool(walletInfo.poolInfo)(p => withWallet(walletInfo.walletName, p)(w => f(w, p)))

  // ************** wallet pool *************
  /**
    * Method to create an instance of wallet pool.
    *
    * @param user          the user who can access the wallet pool.
    * @param poolName      the name of this created pool.
    * @param configuration the extra configuration can be set to the pool.
    * @return a Future of `co.ledger.wallet.daemon.models.Pool` instance.
    */
  def createWalletPool(poolInfo: PoolInfo, configuration: String)(implicit ec: ExecutionContext): Future[Pool] =
    withUser(poolInfo.pubKey)(_.addPoolIfNotExit(poolInfo.poolName, configuration))

  /**
    * Getter of instance of `co.ledger.wallet.daemon.models.Wallet`.
    *
    * @param pubKey   the public key of user who can access the pool.
    * @param poolName the name of wallet pool.
    * @return a Future of `co.ledger.wallet.daemon.models.Pool` instance Option.
    */
  def getWalletPool(poolInfo: PoolInfo)(implicit ec: ExecutionContext): Future[Option[Pool]] =
    withUser(poolInfo.pubKey)(_.pool(poolInfo.poolName))


  def withWalletPool[T](poolInfo: PoolInfo)(f: Pool => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    getWalletPool(poolInfo).flatMap {
      case Some(pool) => f(pool)
      case None => Future.failed(WalletPoolNotFoundException(poolInfo.poolName))
    }
  }

  /**
    * Getter of sequence of instances of `co.ledger.wallet.daemon.models.Pool`.
    *
    * @param pubKey the public key of user who can access the pool.
    * @return a Future of sequence of `co.ledger.wallet.daemon.models.Pool` instances.
    */
  def getWalletPools(pubKey: String)(implicit ec: ExecutionContext): Future[Seq[Pool]] =
    withUser(pubKey)(_.pools())

  /**
    * Method to delete wallet pool instance. This operation will delete the pool record from daemon database and
    * dereference the pool from core library.
    *
    * @param user     the user who can operate the pool.
    * @param poolName the name of the wallet pool needs to be deleted.
    * @return a Future of Unit.
    */
  def deleteWalletPool(poolInfo: PoolInfo)(implicit ec: ExecutionContext): Future[Unit] =
    withUser(poolInfo.pubKey)(_.deletePool(poolInfo.poolName))


  //**************** user ***************
  /**
    * Getter of user instance.
    *
    * @param pubKey the public key related to this user.
    * @return a Future of User instance Option.
    */

  def withUser[T](pubKey: String)(f: User => Future[T])(implicit ec: ExecutionContext): Future[T] =
    getUser(pubKey).flatMap{
      case Some(user) => f(user)
      case None => Future.failed(UserNotFoundException(pubKey))
    }

  /**
    * Method to create a user instance.
    *
    * @param pubKey      public key of this user.
    * @param permissions the permissions level of this user.
    * @return a Future of unique id of created user.
    */
  def createUser(pubKey: String, permissions: Int): Future[Long]

  def getUsers: Future[Seq[User]]

  def getUser(pubKey: String): Future[Option[User]]
}
