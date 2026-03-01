package biz.example.web.undertow.servlet;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for {@link UndertowServletWebServerFactory}.
 *
 * <p>Each test starts a real Undertow listener on a random port (port 0) and drives
 * it with the JDK {@link HttpClient}. A raw {@link HttpServlet} is registered via a
 * {@link ServletContextInitializer} — no Spring WebMVC, no DispatcherServlet — which
 * exercises the full stack:
 * <pre>
 *   UndertowServletWebServerFactory
 *     → DeploymentManagerHttpHandlerFactory
 *     → Undertow 2.4 (XNIO listener)
 *     → Jakarta Servlet 6.1 container
 *     → HttpServlet.doGet()
 * </pre>
 *
 * <p>For unit tests that verify factory state without starting a server see
 * {@link UndertowServletWebServerFactoryTests}.
 */
class UndertowServletWebServerIntegrationTests {

    @Test
    void helloWorldServletReturns200WithBody() throws Exception {
        WebServer server = startWithHelloServlet();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + server.getPort() + "/hello"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello from Undertow");
        }
        finally {
            server.stop();
        }
    }

    @Test
    void unknownPathReturns404() throws Exception {
        WebServer server = startWithHelloServlet();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + server.getPort() + "/not-here"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(404);
        }
        finally {
            server.stop();
        }
    }

    @Test
    void contextPathPrefixIsRequiredWhenSet() throws Exception {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.setContextPath("/api");
        WebServer server = factory.getWebServer(helloWorldInitializer());
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();

            // without context prefix → 404
            HttpResponse<String> notFound = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + server.getPort() + "/hello"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(notFound.statusCode()).isEqualTo(404);

            // with context prefix → 200
            HttpResponse<String> ok = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + server.getPort() + "/api/hello"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(ok.statusCode()).isEqualTo(200);
            assertThat(ok.body()).isEqualTo("Hello from Undertow");
        }
        finally {
            server.stop();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WebServer startWithHelloServlet() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        WebServer server = factory.getWebServer(helloWorldInitializer());
        server.start();
        return server;
    }

    /** Registers a raw {@link HttpServlet} at {@code /hello} — pure Jakarta Servlet 6.1. */
    private ServletContextInitializer helloWorldInitializer() {
        return (servletContext) -> {
            ServletRegistration.Dynamic reg =
                    servletContext.addServlet("hello", new HelloWorldServlet());
            reg.addMapping("/hello");
        };
    }

    static class HelloWorldServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            resp.getWriter().write("Hello from Undertow");
        }

    }

}
