import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test case sends a large portion of text data.
 * It's expected that after send a client socket is closed with error, but it seems nothing is happened.
 *
 * <p>
 * You can start debugging by placing a breakpoint in
 * org.eclipse.jetty.websocket.core.internal.messages.StringMessageSink class on line 44 (when a
 * MessageTooLargeException is thrown).
 */
public class DeflateTest {

    Server server;

    @BeforeEach
    void setUp() throws Exception {
        server = new Server();
        var handler = new ServletContextHandler();
        handler.addServlet(new ServletHolder(new WebSocketServlet()), "/ws");
        server.insertHandler(handler);
        var connector = new ServerConnector(server);
        connector.setPort(8083);
        server.addConnector(connector);
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
    }

    @Test
    void testDeflate() throws Exception {
        var client = new WebSocketClient();
//        client.setMaxTextMessageSize(5 * 1024 * 1024);
        client.start();
        var latch = new CountDownLatch(1);
        var req = new ClientUpgradeRequest();
        req.addExtensions("permessage-deflate");
        var session = client.connect(new Socket(latch), URI.create("ws://localhost:8083/ws"), req).get();
        assertTrue(latch.await(3, TimeUnit.SECONDS), "time out");
        session.close();
    }

    private static class Socket implements WebSocketListener {
        private CountDownLatch latch;

        public Socket(CountDownLatch latch) {
            this.latch = latch;
        }

        private static String largePayloads() {
            var bytes = new byte[4 * 1024 * 1024];
            new Random(42).nextBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public void onWebSocketText(String message) {
            String expected = largePayloads();
            assertEquals(expected, message);
            latch.countDown();
        }

        @Override
        public void onWebSocketConnect(Session session) {
            session.getRemote() //
                    .sendString(largePayloads(), new WriteCallback() {
                        @Override
                        public void writeFailed(Throwable x) {
                            System.out.println("failed");
                        }

                        @Override
                        public void writeSuccess() {
                            System.out.println("ok");
                        }
                    });
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            latch.countDown();
            System.out.println("Web socket closed, reason = " + reason);
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            latch.countDown();
            cause.printStackTrace();
        }
    }
}
