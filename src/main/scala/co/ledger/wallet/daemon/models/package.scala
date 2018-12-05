package co.ledger.wallet.daemon

/**
  * Describe your class here.
  *
  * User: Chenyu LU
  * Date: 22-11-2018
  * Time: 15:22
  *
  */
package object models {
  type BTCPubKey = Array[Byte]
  type BTCSignature = Array[Byte]
  type BTCSigPub = (BTCSignature, BTCPubKey)
  type ETHSignature = Array[Byte]
}
