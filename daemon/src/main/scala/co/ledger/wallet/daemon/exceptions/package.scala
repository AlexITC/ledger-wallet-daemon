package co.ledger.wallet.daemon

import java.util.UUID

package object exceptions {

  case class AccountNotFoundException(            accountIndex: Int) extends DaemonException(s"Account with index $accountIndex doesn't exist")
  case class OperationNotFoundException(               cursor: UUID) extends DaemonException(s"Operation with previous or next cursor $cursor doesn't exist")
  case class WalletNotFoundException(            walletName: String) extends DaemonException(s"Wallet $walletName doesn't exist")
  case class WalletPoolNotFoundException(          poolName: String) extends DaemonException(s"Wallet pool $poolName doesn't exist")
  case class WalletPoolAlreadyExistException(      poolName: String) extends DaemonException(s"Wallet pool $poolName already exists")
  case class CurrencyNotFoundException(        currencyName: String) extends DaemonException(s"Currency $currencyName is not supported")
  case class UserNotFoundException(                  pubKey: String) extends DaemonException(s"User $pubKey doesn't exist")
  case class UserAlreadyExistException(              pubKey: String) extends DaemonException(s"User $pubKey already exists")
  case class InvalidArgumentException(msg: String,     e: Throwable) extends DaemonException(msg, e)
  case class DaemonDatabaseException( msg: String,     e: Throwable = null) extends Exception(msg, e)
  class DaemonException(              msg: String,     e: Throwable = null) extends Exception(msg, e)
}
