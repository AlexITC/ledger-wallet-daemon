package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.controllers.responses.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.models.WalletPool
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.Status

class WalletPoolsApiTest extends APIFeatureTest {

  test("WalletPoolsApi#Create and list single pool") {
    createPool("my_pool")
    val pools = parse[List[models.WalletPool]](getPools())
    val pool = parse[models.WalletPool](getPool("my_pool"))
    assert(pools == List(pool))
    deletePool("my_pool")
  }

  test("WalletPoolsApi#Create pool with invalid name") {
    createPool("my_pool; drop table my_pool,", Status.BadRequest)
  }

  test("WalletPoolsApi#Create and list multiple pool") {
    createPool("your_pool")
    createPool("this_pool")
    val pools = parse[List[models.WalletPool]](getPools())
    assert(pools == List(WalletPool("your_pool", 0), WalletPool("this_pool", 0)))
    deletePool("your_pool")
    deletePool("this_pool")
    val pools2 = parse[List[models.WalletPool]](getPools())
    assert(pools2.size == 0)
  }

  test("WalletPoolsApi#Get single pool") {
    val response = createPool("anotha_pool")
    assert(server.mapper.objectMapper.readValue[models.WalletPool](response.contentString) == WalletPool("anotha_pool", 0))
    val pool = parse[models.WalletPool](getPool("anotha_pool"))
    assert(pool == WalletPool("anotha_pool", 0))
    deletePool("anotha_pool")
  }

  test("WalletPoolsApi#Get and delete non-exist pool return not found") {
    assert(
      server.mapper.objectMapper.readValue[ErrorResponseBody](getPool("not_exist_pool", Status.NotFound).contentString)
        == ErrorResponseBody(ErrorCode.Not_Found, Map("response"->"Wallet pool doesn't exist","pool_name"-> "not_exist_pool")))
    assert(deletePool("another_not_exist_pool").contentString === "")
  }

  test("WalletPoolsApi#Create same pool twice return ok") {
    assert(
      server.mapper.objectMapper.readValue[models.WalletPool](createPool("same_pool").contentString)
        == WalletPool("same_pool", 0))
    assert(
      server.mapper.objectMapper.readValue[models.WalletPool](createPool("same_pool").contentString)
        == WalletPool("same_pool", 0))
    deletePool("same_pool")
  }

}
