package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.wallet.daemon.models.coins.Coin.{BlockView, NetworkParamsView, TransactionView}
import com.fasterxml.jackson.annotation.JsonProperty

case class EthereumNetworkParameter() extends NetworkParamsView

case class EthereumBlockView(
                              @JsonProperty("hash") hash: String,
                              @JsonProperty("height") height: Long,
                              @JsonProperty("time") time: Date
                            ) extends BlockView

case class EthereumTransactionView(
                                    @JsonProperty("hash") hash: String,
                                  ) extends TransactionView
