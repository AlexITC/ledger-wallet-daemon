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

  // ************** account *************
  /**
    * Method to create an account instance. The account may already exist in the library.
    *
    * @param accountDerivation derivation information specified for this account.
    * @param user              the user who can access the account.
    * @param poolName          the name of the wallet pool the account belongs to.
    * @param walletName        the name of the wallet the account belongs to.
    * @return a Future of new instance of `co.ledger.wallet.daemon.models.Account`.
    */
  def createAccount(accountDerivation: AccountDerivationView, poolName: String, walletName: String, pubKey: String)(implicit ec: ExecutionContext): Future[Account] =
    withWallet(walletName, poolName, pubKey)(w => createAccount(accountDerivation, w))

  def createAccount(accountDerivation: AccountDerivationView, wallet: Wallet)(implicit ec: ExecutionContext): Future[Account] =
    wallet.addAccountIfNotExist(accountDerivation)

  /**
    * Method to create an account instance with extended account. The account may already exist in the library.
    *
    * @param accountDerivation derivation information specified for this account.
    * @param user              the user who can access the account.
    * @param poolName          the name of the wallet pool the account belongs to.
    * @param walletName        the name of the wallet the account belongs to.
    * @return a Future of new instance of `co.ledger.wallet.daemon.models.Account`.
    */
  def createAccount(accountDerivation: AccountExtendedDerivationView, poolName: String, walletName: String, pubKey: String)(implicit ec: ExecutionContext): Future[Account] =
    withWallet(walletName, poolName, pubKey)(w => createAccount(accountDerivation, w))

  def createAccount(accountDerivation: AccountExtendedDerivationView, wallet: Wallet)(implicit ec: ExecutionContext): Future[Account] =
    wallet.addAccountIfNotExist(accountDerivation)

  /**
    * Getter of accounts sequence with specified parameters.
    *
    * @param pubKey     the public key of instance of `co.ledger.wallet.daemon.DefaultDaemonCache.User`.
    * @param poolName   the name of wallet pool the account belongs to.
    * @param walletName the name of wallet the account belongs to.
    * @return a Future of a sequence of instances of `co.ledger.wallet.daemon.models.Account`.
    */
  def getAccounts(pubKey: String, poolName: String, walletName: String)(implicit ec: ExecutionContext): Future[Seq[Account]] =
    withWallet(walletName, poolName, pubKey)(_.accounts)

  /**
    * Getter of account instance with specified parameters.
    *
    * @param accountIndex the unique index of specified instance.
    * @param pubKey       the public key of instance of `co.ledger.wallet.daemon.DefaultDaemonCache.User`.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @return a Future of an Option of the instance of `co.ledger.wallet.daemon.models.Account`.
    */
  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String)(implicit ec: ExecutionContext): Future[Option[Account]] =
    withWallet(walletName, poolName, pubKey)(_.account(accountIndex))

  def withAccount[T](accountIndex: Int, walletName: String, poolName: String, pubKey: String)(f: Account => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withWallet(walletName, poolName, pubKey)(w => withAccount(accountIndex, w)(f))

  def withAccount[T](accountIndex: Int, wallet: Wallet)(f: Account => Future[T])(implicit ec: ExecutionContext): Future[T] =
    wallet.account(accountIndex).flatMap {
      case Some(account) => f(account)
      case None => Future.failed(AccountNotFoundException(accountIndex))
    }

  def withAccountAndWallet[T](accountIndex: Int, walletName: String, poolName: String, pubKey: String)(f: (Account, Wallet) => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withAccountAndWalletAndPool(accountIndex, walletName, poolName, pubKey){
      case (account, wallet, _) => f(account, wallet)
    }

  def withAccountAndWalletAndPool[T](accountIndex: Int, walletName: String, poolName: String, pubKey: String)(f: (Account, Wallet, Pool) => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withWalletPool(pubKey, poolName) { pool =>
      withWallet(walletName, pool) { wallet =>
        withAccount(accountIndex, wallet) { account =>
          f(account, wallet, pool)
        }
      }
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
  def getDerivationPath(accountIndex: Int, pubKey: String, poolName: String, walletName: String)(implicit ec: ExecutionContext): Future[String] =
    withWallet(walletName, poolName, pubKey)(_.accountDerivationPathInfo(accountIndex))


  /**
    * Getter for fresh addresses of specified account.
    *
    * @param accountIndex the unique index of specified account.
    * @param user         the user who can access the account.
    * @param poolName     the name of wallet pool the account belongs to.
    * @param walletName   the name of wallet the account belongs to.
    * @return a Future of a sequence of instances of `co.ledger.wallet.daemon.models.Account`.
    */
  def getFreshAddresses(accountIndex: Int, poolName: String, walletName: String, pubKey: String)(implicit ec: ExecutionContext): Future[Seq[FreshAddressView]] =
    withAccount(accountIndex, walletName, poolName, pubKey)(_.freshAddresses).map(_.map(addr => FreshAddressView(addr.toString, addr.getDerivationPath)))

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
  def getAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, batch: Int, fullOp: Int): Future[PackedOperationsView]

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
  def getAccountOperation(uid: String, pubKey: String, accountIndex: Int, poolName: String, walletName: String, fullOp: Int)(implicit ec: ExecutionContext): Future[Option[OperationView]] =
    withAccountAndWallet(accountIndex, walletName, poolName, pubKey) {
      case (account, wallet) =>
        for {
          operationOpt <- account.operation(uid, fullOp)
          op <- operationOpt match {
            case None => Future(None)
            case Some(op) => Operations.getView(op, wallet, account).map(Some(_))
          }
        } yield op
    }

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
  def getNextBatchAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, next: UUID, fullOp: Int): Future[PackedOperationsView]

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
  def getPreviousBatchAccountOperations(
                                         user: User,
                                         accountIndex: Int,
                                         poolName: String,
                                         walletName: String,
                                         previous: UUID,
                                         fullOp: Int): Future[PackedOperationsView]


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
  def getNextExtendedAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int])(implicit ec: ExecutionContext): Future[ExtendedDerivation] =
    withWallet(walletName, poolName, pubKey)(_.accountExtendedCreation(accountIndex))

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
  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int])(implicit ec: ExecutionContext): Future[Derivation] =
    withWallet(walletName, poolName, pubKey)(_.accountCreationInfo(accountIndex))

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
  def getCurrency(currencyName: String, poolName: String, pubKey: String)(implicit ec: ExecutionContext): Future[Option[Currency]] =
    withWalletPool(pubKey, poolName)(_.currency(currencyName))

  /**
    * Getter of `co.ledger.wallet.daemon.models.Currency` instances sequence.
    *
    * @param poolName the name of wallet pool of this currency.
    * @param pubKey   the public key of user.
    * @return a Future of sequence of `co.ledger.wallet.daemon.models.Currency` instances.
    */
  def getCurrencies(poolName: String, pubKey: String)(implicit ec: ExecutionContext): Future[Seq[Currency]] =
    withWalletPool(pubKey, poolName)(_.currencies())

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
  def createWallet(walletName: String, currencyName: String, poolName: String, pubKey: String)(implicit ec: ExecutionContext): Future[Wallet] = {
    withWalletPool(pubKey, poolName)(_.addWalletIfNotExist(walletName, currencyName))
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
  def getWallets(offset: Int, batch: Int, poolName: String, pubKey: String)(implicit ec: ExecutionContext): Future[(Int, Seq[Wallet])] = {
    withWalletPool(pubKey, poolName)(_.wallets(offset, batch))
  }

  /**
    * Getter of instance of `co.ledger.wallet.daemon.models.Wallet`.
    *
    * @param walletName the name of specified wallet.
    * @param poolName   the name of the pool the wallet belongs to.
    * @param pubKey     the public key of the user.
    * @return a Future of `co.ledger.wallet.daemon.models.Wallet` instance Option.
    */
  def getWallet(walletName: String, poolName: String, pubKey: String)(implicit ec: ExecutionContext): Future[Option[Wallet]] = {
    withWalletPool(pubKey, poolName)(_.wallet(walletName))
  }

  def withWallet[T](walletName: String, poolName: String, pubKey: String)(f: Wallet => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withWalletPool(pubKey, poolName)(p => withWallet(walletName, p)(f))

  def withWallet[T](walletName: String, pool: Pool)(f: Wallet => Future[T])(implicit ec: ExecutionContext): Future[T] =
    pool.wallet(walletName).flatMap {
      case Some(w) => f(w)
      case None => Future.failed(WalletNotFoundException(walletName))
    }

  def withWalletAndPool[T](walletName: String, poolName: String, pubKey: String)(f: (Wallet, Pool) => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withWalletPool(pubKey, poolName)(p => withWallet(walletName, p)(w => f(w, p)))

  // ************** wallet pool *************
  /**
    * Method to create an instance of wallet pool.
    *
    * @param user          the user who can access the wallet pool.
    * @param poolName      the name of this created pool.
    * @param configuration the extra configuration can be set to the pool.
    * @return a Future of `co.ledger.wallet.daemon.models.Pool` instance.
    */
  def createWalletPool(pubKey: String, poolName: String, configuration: String)(implicit ec: ExecutionContext): Future[Pool] =
    withUser(pubKey)(_.addPoolIfNotExit(poolName, configuration))

  /**
    * Getter of instance of `co.ledger.wallet.daemon.models.Wallet`.
    *
    * @param pubKey   the public key of user who can access the pool.
    * @param poolName the name of wallet pool.
    * @return a Future of `co.ledger.wallet.daemon.models.Pool` instance Option.
    */
  def getWalletPool(pubKey: String, poolName: String)(implicit ec: ExecutionContext): Future[Option[Pool]] =
    withUser(pubKey)(_.pool(poolName))


  def withWalletPool[T](pubKey: String, poolName: String)(f: Pool => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    getWalletPool(pubKey, poolName).flatMap {
      case Some(pool) => f(pool)
      case None => Future.failed(WalletPoolNotFoundException(poolName))
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
  def deleteWalletPool(pubKey: String, poolName: String)(implicit ec: ExecutionContext): Future[Unit] =
    withUser(pubKey)(_.deletePool(poolName))

  /**
    * Method to synchronize account operations from public resources. The method may take a while
    * to finish.
    *
    * @return a Future of sequence of result of synchronization.
    */
  def syncOperations(implicit ec: ExecutionContext): Future[Seq[SynchronizationResult]] =
    getUsers.flatMap { us =>
      Future.sequence(us.map { user => user.sync()}).map (_.flatten)
    }


  /**
    * Method to synchronize account operations from public resources. The method may take a while
    * to finish. This method only synchronize a single pool.
    *
    * @return a Future of sequence of result of synchronization.
    */
  def syncOperations(pubKey: String, poolName: String)(implicit ec: ExecutionContext): Future[Seq[SynchronizationResult]] =
    withWalletPool(pubKey, poolName)(_.sync())

  /**
    * Method to synchronize account operations from public resources. The method may take a while
    * to finish. This method onlly synchronize a single account.
    *
    * @return a Future of sequence of result of synchronization.
    */
  def syncOperations(pubKey: String, poolName: String, walletName: String, accountIndex: Int)(implicit ec: ExecutionContext): Future[Seq[SynchronizationResult]] =
    withAccount(accountIndex, walletName, poolName, pubKey)(_.sync(poolName, walletName).map(Seq(_)))

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
