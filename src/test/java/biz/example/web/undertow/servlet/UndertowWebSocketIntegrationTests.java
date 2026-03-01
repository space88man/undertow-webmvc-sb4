package biz.example.web.undertow.servlet;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import biz.example.web.undertow.autoconfigure.servlet.UndertowWebSocketServletWebServerCustomizer;
import org.springframework.boot.web.server.WebServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for WebSocket support in {@link UndertowServletWebServerFactory}.
 *
 * <p>Each test starts a real Undertow listener on a random port and performs an HTTP/1.1
 * Upgrade to WebSocket using the JDK {@link HttpClient}. No Spring WebMVC or
 * {@code DispatcherServlet} is involved — the stack under test is:
 * <pre>
 *   JDK WebSocket client
 *     → HTTP/1.1 Upgrade (RFC 6455)
 *     → Undertow 2.4 (XNIO + WSS bootstrap)
 *     → Jakarta WebSocket 2.2 container (JSR-356)
 *     → {@link EchoEndpoint} (@ServerEndpoint, @OnMessage)
 * </pre>
 *
 * <p>The RPC contract exercised here:
 * <pre>
 *   client sends:   {"message":"&lt;text&gt;"}
 *   server echoes:  {"echo client message":"&lt;text&gt;"}
 * </pre>
 *
 * <p>WebSocket support is activated via {@link UndertowWebSocketServletWebServerCustomizer},
 * exactly as it would be in a real Spring Boot application context. The endpoint is
 * registered by adding a second {@code UndertowDeploymentInfoCustomizer} that retrieves
 * the {@link WebSocketDeploymentInfo} created by the WS customizer and calls
 * {@link WebSocketDeploymentInfo#addEndpoint(Class)}.
 */
class UndertowWebSocketIntegrationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void http11UpgradeEchoesJsonMessage() throws Exception {
        WebServer server = startWithEchoEndpoint();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> received = new AtomicReference<>();

            // Connect — the JDK Upgrade handshake occurs inside buildAsync()
            WebSocket ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + server.getPort() + "/echo"),
                            new WebSocket.Listener() {
                                @Override
                                public CompletionStage<?> onText(WebSocket webSocket,
                                        CharSequence data, boolean last) {
                                    received.set(data.toString());
                                    latch.countDown();
                                    return WebSocket.Listener.super.onText(webSocket, data, last);
                                }
                            })
                    .join();

            // Build and send: {"message":"hello undertow"}
            ObjectNode request = MAPPER.createObjectNode();
            request.put("message", "hello undertow");
            ws.sendText(MAPPER.writeValueAsString(request), /* last */ true).join();

            assertThat(latch.await(5, TimeUnit.SECONDS))
                    .as("timed out waiting for echo response")
                    .isTrue();

            // Verify: {"echo client message":"hello undertow"}
            ObjectNode response = (ObjectNode) MAPPER.readTree(received.get());
            assertThat(response.get("echo client message").asString())
                    .isEqualTo("hello undertow");

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
        finally {
            server.stop();
        }
    }

    @Test
    void multipleMessagesAreEachedIndependently() throws Exception {
        WebServer server = startWithEchoEndpoint();
        try {
            int messageCount = 3;
            CountDownLatch latch = new CountDownLatch(messageCount);
            @SuppressWarnings("unchecked")
            AtomicReference<String>[] responses = new AtomicReference[messageCount];
            for (int i = 0; i < messageCount; i++) {
                responses[i] = new AtomicReference<>();
            }
            int[] index = {0};

            WebSocket ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + server.getPort() + "/echo"),
                            new WebSocket.Listener() {
                                @Override
                                public CompletionStage<?> onText(WebSocket webSocket,
                                        CharSequence data, boolean last) {
                                    int i = index[0]++;
                                    if (i < messageCount) {
                                        responses[i].set(data.toString());
                                        latch.countDown();
                                    }
                                    return WebSocket.Listener.super.onText(webSocket, data, last);
                                }
                            })
                    .join();

            String[] payloads = {"alpha", "beta", "gamma"};
            for (String payload : payloads) {
                ObjectNode req = MAPPER.createObjectNode();
                req.put("message", payload);
                ws.sendText(MAPPER.writeValueAsString(req), true).join();
            }

            assertThat(latch.await(5, TimeUnit.SECONDS))
                    .as("timed out waiting for %d echo responses", messageCount)
                    .isTrue();

            for (int i = 0; i < messageCount; i++) {
                ObjectNode resp = (ObjectNode) MAPPER.readTree(responses[i].get());
                assertThat(resp.get("echo client message").asString())
                        .as("response[%d]", i)
                        .isEqualTo(payloads[i]);
            }

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
        finally {
            server.stop();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a factory with WebSocket support enabled and {@link EchoEndpoint} registered,
     * then starts the server on a random port.
     */
    private WebServer startWithEchoEndpoint() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);

        // Step 1: activate the WebSocket container (adds blank WebSocketDeploymentInfo)
        new UndertowWebSocketServletWebServerCustomizer().customize(factory);

        // Step 2: register our annotated endpoint into the WebSocketDeploymentInfo
        // that was just added by the WS customizer (customizers run in registration order)
        factory.addDeploymentInfoCustomizers((DeploymentInfo deploymentInfo) -> {
            WebSocketDeploymentInfo wsInfo = (WebSocketDeploymentInfo)
                    deploymentInfo.getServletContextAttributes()
                                  .get(WebSocketDeploymentInfo.ATTRIBUTE_NAME);
            wsInfo.addEndpoint(EchoEndpoint.class);
        });

        WebServer server = factory.getWebServer();
        server.start();
        return server;
    }

    // ── Server-side endpoint ──────────────────────────────────────────────────

    /**
     * Annotated Jakarta WebSocket endpoint registered at {@code /echo}.
     *
     * <p>Accepts: {@code {"message":"<text>"}}
     * <p>Returns: {@code {"echo client message":"<text>"}}
     *
     * <p>Must be {@code public static} so the Jakarta container can instantiate it.
     */
    @ServerEndpoint("/echo")
    public static class EchoEndpoint {

        private final ObjectMapper mapper = new ObjectMapper();

        /**
         * Called by the container for each complete text message.
         * The returned {@link String} is automatically sent back to the caller.
         */
        @OnMessage
        public String onMessage(String text, Session session) throws Exception {
            ObjectNode in = (ObjectNode) mapper.readTree(text);
            String msg = in.get("message").asString();

            ObjectNode out = mapper.createObjectNode();
            out.put("echo client message", msg);
            return mapper.writeValueAsString(out);
        }

    }

}
