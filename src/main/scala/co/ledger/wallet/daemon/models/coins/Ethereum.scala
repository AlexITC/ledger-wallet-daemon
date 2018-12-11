package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core.{ERC20LikeOperation, EthereumLikeBlock, EthereumLikeNetworkParameters, EthereumLikeTransaction}
import co.ledger.wallet.daemon.models.coins.Coin.{BlockView, NetworkParamsView, TransactionView}
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._

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

object EthereumBlockView {
  def apply(b: EthereumLikeBlock): EthereumBlockView = {
    EthereumBlockView(b.getHash, b.getHeight, b.getTime)
  }
}

case class EthereumTransactionView(
                                    @JsonProperty("block") block: Option[EthereumBlockView],
                                    @JsonProperty("hash") hash: String,
                                    @JsonProperty("receiver") receiver: String,
                                    @JsonProperty("sender") sender: String,
                                    @JsonProperty("value") value: Long,
                                    @JsonProperty("gas_price") gasPrice: Long,
                                    @JsonProperty("gas_limit") gasLimit: Long,
                                    @JsonProperty("date") date: Date
                                  ) extends TransactionView

object EthereumTransactionView {
  def apply(tx: EthereumLikeTransaction): EthereumTransactionView = {
    EthereumTransactionView(
      Option(tx.getBlock).map(EthereumBlockView.apply),
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

case class UnsignedEthereumTransactionView(
                                            @JsonProperty("hash") hash: String,
                                            @JsonProperty("receiver") receiver: String,
                                            @JsonProperty("value") value: Long,
                                            @JsonProperty("gas_price") gasPrice: Long,
                                            @JsonProperty("gas_limit") gasLimit: Long,
                                            @JsonProperty("raw_transaction") rawTransaction: String
                                          ) extends TransactionView

object UnsignedEthereumTransactionView {
  def apply(tx: EthereumLikeTransaction): UnsignedEthereumTransactionView = {
    UnsignedEthereumTransactionView(
      tx.getHash,
      tx.getReceiver.toEIP55,
      tx.getValue.toLong,
      tx.getGasPrice.toLong,
      tx.getGasLimit.toLong,
      HexUtils.valueOf(tx.serialize())
    )
  }
}

// TODO refine operation view
case class ERC20OperationView(
                             @JsonProperty("sender") sender: String,
                             @JsonProperty("receiver") receiver: String,
                             @JsonProperty("value") value: Long,
                             @JsonProperty("gas_price") gasPrice: Long,
                             @JsonProperty("gas_limit") gasLimit: Long
                             )

object ERC20OperationView {
  def apply(op: ERC20LikeOperation): ERC20OperationView = {
    apply(
      op.getSender,
      op.getReceiver,
      // TODO int is dangerous here, find a way to Long
      op.getValue.intValue(),
      op.getGasPrice.intValue(),
      op.getGasLimit.intValue()
    )
  }
}
