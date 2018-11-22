package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core.EthereumLikeTransaction
import co.ledger.wallet.daemon.models.coins.Coin.{BlockView, NetworkParamsView, TransactionView}
import com.fasterxml.jackson.annotation.JsonProperty

object Ethereum {
  def newUnsignedTransactionView(tx: EthereumLikeTransaction): EthereumTransactionView = {
    EthereumTransactionView(
      tx.getHash,
      tx.getReceiver.toEIP55,
      tx.getSender.toEIP55,
      tx.getValue.toLong,
      tx.getGasPrice.toLong,
      tx.getGasLimit.toLong,
      tx.getDate,
    )
  }
}

case class EthereumNetworkParameter() extends NetworkParamsView

case class EthereumBlockView(
                              @JsonProperty("hash") hash: String,
                              @JsonProperty("height") height: Long,
                              @JsonProperty("time") time: Date
                            ) extends BlockView

case class EthereumTransactionView(
                                    @JsonProperty("hash") hash: String,
                                    @JsonProperty("receiver") receiver: String,
                                    @JsonProperty("sender") sender: String,
                                    @JsonProperty("value") value: Long,
                                    @JsonProperty("gas_price") gasPrice: Long,
                                    @JsonProperty("gas_limit") gasLimit: Long,
                                    @JsonProperty("date") date: Date
                                  ) extends TransactionView
