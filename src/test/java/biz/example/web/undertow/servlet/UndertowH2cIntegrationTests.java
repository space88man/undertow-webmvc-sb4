package biz.example.web.undertow.servlet;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.Http2;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests verifying HTTP/2 cleartext (H2C) support in
 * {@link UndertowServletWebServerFactory}.
 *
 * <p>HTTP/2 is purely an embedded-server concern — Spring WebMVC has no knowledge
 * of the wire protocol. Undertow is configured at the XNIO layer via
 * {@code UndertowOptions.ENABLE_HTTP2}, which our factory honours through
 * {@link org.springframework.boot.web.server.Http2}.
 *
 * <p>Two protocol paths are tested:
 * <ol>
 *   <li><b>H2C prior knowledge at the HTTP layer</b> — client connects with plaintext
 *       HTTP/2 directly (no HTTP/1.1 Upgrade negotiation).  Verified via
 *       {@code curl --http2-prior-knowledge} reporting {@code http_version = 2}.</li>
 *   <li><b>H2C visible inside the Servlet container</b> — the same H2C request reaches
 *       a registered {@link HttpServlet} which reads {@code request.getProtocol()}.
 *       The response body is asserted to contain {@code "HTTP/2"}, proving the full
 *       path: curl H2C → Undertow XNIO → Jakarta Servlet 6.1 container →
 *       {@code HttpServletRequest.getProtocol()}.</li>
 * </ol>
 *
 * <p><b>Note on WebSocket over H2C (RFC 8441):</b> curl always uses HTTP/1.1 Upgrade
 * for {@code ws://} URLs regardless of {@code --http2-prior-knowledge} — it does not
 * implement RFC 8441 (HTTP/2 extended CONNECT). An RFC 8441 test would require a
 * dedicated HTTP/2 WebSocket client library (e.g. Netty or Jetty http2-client), which
 * are out of scope for this module.  The WebSocket-over-HTTP/1.1 path is covered
 * separately in {@link UndertowWebSocketIntegrationTests}.
 *
 * <p>curl 8.x ({@code --http2-prior-knowledge}) is used as the H2C client throughout.
 */
class UndertowH2cIntegrationTests {

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Verifies that Undertow speaks HTTP/2 cleartext when a client connects with
     * prior knowledge, i.e. without HTTP/1.1 Upgrade negotiation.
     *
     * <p>A bare server with no servlet is sufficient — the 404 response is still
     * delivered over HTTP/2, which is all we need to assert.
     */
    @Test
    void h2cPriorKnowledgeReportsHttp2Version() throws Exception {
        WebServer server = startWithHttp2();
        try {
            CurlResult result = curl(
                    "--http2-prior-knowledge",
                    "-s",                           // silent — no progress meter
                    "-o", "/dev/null",              // discard body
                    "-w", "%{http_version}",        // write only the negotiated version
                    "http://localhost:" + server.getPort() + "/");

            // curl exit code 0 means the TCP connection and HTTP/2 handshake succeeded
            assertThat(result.exitCode).as("curl exit code").isEqualTo(0);
            assertThat(result.stdout.trim())
                    .as("negotiated HTTP version")
                    .isEqualTo("2");
        }
        finally {
            server.stop();
        }
    }

    /**
     * Verifies that an H2C request is visible to the Jakarta Servlet container as
     * {@code HTTP/2.0} via {@link HttpServletRequest#getProtocol()}.
     *
     * <p>This proves the full path: curl H2C → Undertow XNIO decoder →
     * Jakarta Servlet 6.1 container → {@code HttpServletRequest.getProtocol()}.
     * The protocol version string is written directly to the response body and
     * asserted by the test.
     */
    @Test
    void servletSeesHttp2ProtocolWhenRequestArrivesOverH2c() throws Exception {
        WebServer server = startWithHttp2AndProtocolServlet();
        try {
            CurlResult result = curl(
                    "--http2-prior-knowledge",
                    "-s",
                    "http://localhost:" + server.getPort() + "/protocol");

            assertThat(result.exitCode).as("curl exit code").isEqualTo(0);
            // HttpServletRequest.getProtocol() returns "HTTP/2.0" for HTTP/2 requests
            assertThat(result.stdout.trim())
                    .as("HttpServletRequest.getProtocol() over H2C")
                    .isEqualTo("HTTP/2.0");
        }
        finally {
            server.stop();
        }
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    private WebServer startWithHttp2() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.setHttp2(enabledHttp2());
        WebServer server = factory.getWebServer();
        server.start();
        return server;
    }

    /**
     * Starts a server with HTTP/2 and a servlet at {@code /protocol} that writes
     * {@code request.getProtocol()} to the response body.
     */
    private WebServer startWithHttp2AndProtocolServlet() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.setHttp2(enabledHttp2());
        WebServer server = factory.getWebServer(protocolEchoInitializer());
        server.start();
        return server;
    }

    private static ServletContextInitializer protocolEchoInitializer() {
        return (servletContext) -> {
            var reg = servletContext.addServlet("protocol", new ProtocolServlet());
            reg.addMapping("/protocol");
        };
    }

    private static Http2 enabledHttp2() {
        Http2 http2 = new Http2();
        http2.setEnabled(true);
        return http2;
    }

    // ── curl subprocess helper ────────────────────────────────────────────────

    private record CurlResult(int exitCode, String stdout, String stderr) {}

    /**
     * Runs curl with the given arguments, waits up to 15 seconds, and returns stdout,
     * stderr, and the exit code.  Both output streams are drained concurrently on
     * background threads to prevent OS pipe-buffer deadlock.
     */
    private static CurlResult curl(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "curl";
        System.arraycopy(args, 0, cmd, 1, args.length);

        Process process = new ProcessBuilder(cmd)
                .redirectInput(new File("/dev/null"))
                .start();

        // Drain both streams concurrently — sequential readAllBytes() can deadlock
        // if one pipe fills while the JVM is blocked reading the other.
        CompletableFuture<byte[]> stdoutFuture = CompletableFuture.supplyAsync(() -> {
            try { return process.getInputStream().readAllBytes(); }
            catch (IOException e) { return new byte[0]; }
        });
        CompletableFuture<byte[]> stderrFuture = CompletableFuture.supplyAsync(() -> {
            try { return process.getErrorStream().readAllBytes(); }
            catch (IOException e) { return new byte[0]; }
        });

        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }

        return new CurlResult(
                process.exitValue(),
                new String(stdoutFuture.join()),
                new String(stderrFuture.join()));
    }

    // ── Servlets ──────────────────────────────────────────────────────────────

    /**
     * Servlet that writes {@link HttpServletRequest#getProtocol()} to the response,
     * allowing the test to assert the wire protocol seen by the Servlet container.
     */
    static class ProtocolServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            resp.getWriter().write(req.getProtocol());
        }

    }

}
