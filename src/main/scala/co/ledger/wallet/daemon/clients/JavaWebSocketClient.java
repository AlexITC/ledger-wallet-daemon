package co.ledger.wallet.daemon.clients;

import javax.websocket.OnMessage;
import javax.websocket.Session;

/**
 * This class is created to contain the processMessage Method.
 * We can't write it in Scala class since it will generate unused
 * parameter warning, then fail the compilation since we have
 * fatal warning. And we have no way to avoid it.
 * <p>
 * User: Chenyu LU
 * Date: 08-01-2019
 * Time: 10:47
 */
public abstract class JavaWebSocketClient extends co.ledger.core.WebSocketClient{
    // here to avoid error "java.lang.IllegalStateException: Text message handler not found".
    @SuppressWarnings({"unused"})
    @OnMessage
    public void processMessage(String message, Session session) {}
}
