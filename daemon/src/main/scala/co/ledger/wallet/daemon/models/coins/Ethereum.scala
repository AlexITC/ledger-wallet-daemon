package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core.{EthereumLikeNetworkParameters, EthereumLikeTransaction}
import co.ledger.wallet.daemon.models.coins.Coin.{BlockView, NetworkParamsView, TransactionView}
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import scala.collection.JavaConverters._

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

case class EthereumNetworkParamView(
                                     @JsonProperty("identifier") identifier: String,
                                     @JsonProperty("message_prefix") messagePrefix: String,
                                     @JsonProperty("xpub_version") xpubVersion: String,
                                     @JsonProperty("additional_eips") additionalEIPs: List[String],
                                     @JsonProperty("timestamp_delay") timestampDelay: Long
                                   ) extends NetworkParamsView

object EthereumNetworkParamView {
  def apply(n: EthereumLikeNetworkParameters): EthereumNetworkParamView =
    EthereumNetworkParamView(
      n.getIdentifier,
      n.getMessagePrefix,
      HexUtils.valueOf(n.getXPUBVersion),
      n.getAdditionalEIPs.asScala.toList,
      n.getTimestampDelay)
}

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
