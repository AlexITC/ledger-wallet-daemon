package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.models.{PoolInfo, WalletPoolView}
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PoolsService @Inject()(daemonCache: DaemonCache) extends DaemonService {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import PoolsService._

  def createPool(poolInfo: PoolInfo, configuration: PoolConfiguration): Future[WalletPoolView] = {
    daemonCache.createWalletPool(poolInfo, configuration.toString).flatMap(_.view)
  }

  def pools(user: User): Future[Seq[WalletPoolView]] = {
    daemonCache.getWalletPools(user.pubKey).flatMap { pools => Future.sequence(pools.map(_.view))}
  }

  def pool(poolInfo: PoolInfo): Future[Option[WalletPoolView]] = {
    daemonCache.getWalletPool(poolInfo).flatMap {
      case Some(pool) => pool.view.map(Option(_))
      case None => Future(None)
    }
  }

  def syncOperations(poolInfo: PoolInfo): Future[Seq[SynchronizationResult]] = {
    daemonCache.syncOperations(poolInfo)
  }

  def syncOperations(): Future[Seq[SynchronizationResult]] = {
    daemonCache.syncOperations
  }

  def removePool(poolInfo: PoolInfo): Future[Unit] = {
    daemonCache.deleteWalletPool(poolInfo)
  }

}

object PoolsService {

  case class PoolConfiguration() {
    override def toString: String = ""
  }
}