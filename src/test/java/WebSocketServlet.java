import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.core.server.WebSocketServerComponents;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;

import javax.servlet.ServletException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

public class WebSocketServlet extends JettyWebSocketServlet {

    @Override
    public void init() throws ServletException {
        var ctx = getServletContext();
        var ctxHandler = ServletContextHandler.getServletContextHandler(ctx);
        WebSocketServerComponents.ensureWebSocketComponents(ctxHandler.getServer(), ctx);
        JettyWebSocketServerContainer.ensureContainer(ctx);
        super.init();
    }


    @Override
    public void configure(JettyWebSocketServletFactory wsFactory) {
        wsFactory.setMaxBinaryMessageSize(5 * 1024 * 1024);
        wsFactory.setCreator((req, resp) -> {
            removeAttributes(req);
            return new WebSocketListener() {
                private RemoteEndpoint remote;

                @Override
                public void onWebSocketBinary(byte[] payload, int offset, int len) {
                    remote.sendBytes(ByteBuffer.wrap(payload, offset, len), new WriteCallback() {
                        @Override
                        public void writeFailed(Throwable x) {
                        }

                        @Override
                        public void writeSuccess() {
                        }
                    });
                }

                @Override
                public void onWebSocketText(String message) {
                    remote.sendBytes(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)), new WriteCallback() {
                        @Override
                        public void writeFailed(Throwable x) {
                        }

                        @Override
                        public void writeSuccess() {
                        }
                    });
                }

                @Override
                public void onWebSocketClose(int statusCode, String reason) {
                    System.out.println("closed");
                }

                @Override
                public void onWebSocketConnect(Session session) {
                    System.out.println("connected");
                    this.remote = session.getRemote();
                }

                @Override
                public void onWebSocketError(Throwable cause) {
                }
            };
        });
    }

    private void removeAttributes(JettyServerUpgradeRequest jettyRequest) {
        var req = jettyRequest.getHttpServletRequest();
        for (String attributeName : new HashSet<>(jettyRequest.getServletAttributes().keySet())) {
            req.removeAttribute(attributeName);
        }
    }
}
