package co.ledger.wallet.daemon.models.coins


object Coin {

  trait NetworkParamsView

  trait TransactionView

  trait BlockView

  trait InputView

  trait OutputView
}


trait Network {
  type Block
  type Transaction
}

object Network {
  def createTransaction[T <: Network](n: T): n.Block = ???
}
