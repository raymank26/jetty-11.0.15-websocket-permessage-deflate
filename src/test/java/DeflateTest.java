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
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        client.setMaxBinaryMessageSize(5 * 1024 * 1024);
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

        private static byte[] largePayloads() {
            var bytes = new byte[4 * 1024 * 1024];
            new Random(42).nextBytes(bytes);
            return bytes;
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            assertArrayEquals(largePayloads(), payload);
            latch.countDown();
        }

        @Override
        public void onWebSocketConnect(Session session) {
            session.getRemote() //
                    .sendBytes(ByteBuffer.wrap(largePayloads()), new WriteCallback() {
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

        public void onWebSocketError(Throwable cause) {
            cause.printStackTrace();
        }
    }
}
