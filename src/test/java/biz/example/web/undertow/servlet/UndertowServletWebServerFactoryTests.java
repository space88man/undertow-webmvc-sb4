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
import org.springframework.boot.web.server.servlet.ServletWebServerSettings;
import org.springframework.boot.web.servlet.ServletContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link UndertowServletWebServerFactory}.
 *
 * Validates:
 * - Factory construction and default state
 * - {@code getSettings()} returns a live, non-null {@link ServletWebServerSettings}
 * - {@link UndertowDeploymentInfoCustomizer} beans are called during deployment setup
 * - Property setters for {@code eagerFilterInit} and {@code preservePathOnForward}
 *   are written into the factory state (not validated during full server start)
 * - {@code getWebServer()} produces an {@link UndertowServletWebServer} instance
 *
 * No real Undertow server is started in the majority of tests; the factory state
 * is inspected directly after mutation to keep tests fast and dependency-free.
 */
class UndertowServletWebServerFactoryTests {

    @Test
    void getSettingsReturnsNonNullSettings() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        assertThat(factory.getSettings()).isNotNull()
                .isInstanceOf(ServletWebServerSettings.class);
    }

    @Test
    void getSettingsReturnsSameLiveInstance() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        // Subsequent calls must return the same object — not a new copy each time
        assertThat(factory.getSettings()).isSameAs(factory.getSettings());
    }

    @Test
    void eagerFilterInitDefaultsToTrue() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        assertThat(factory.isEagerFilterInit()).isTrue();
    }

    @Test
    void eagerFilterInitCanBeSetToFalse() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        factory.setEagerFilterInit(false);
        assertThat(factory.isEagerFilterInit()).isFalse();
    }

    @Test
    void preservePathOnForwardDefaultsToFalse() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        assertThat(factory.isPreservePathOnForward()).isFalse();
    }

    @Test
    void preservePathOnForwardCanBeSetToTrue() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        factory.setPreservePathOnForward(true);
        assertThat(factory.isPreservePathOnForward()).isTrue();
    }

    @Test
    void deploymentInfoCustomizerIsInvokedWhenServerStarts() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);

        // Record whether the customizer was invoked during getWebServer()
        boolean[] called = {false};
        factory.addDeploymentInfoCustomizers((deploymentInfo) -> called[0] = true);

        WebServer webServer = factory.getWebServer();
        webServer.start();
        try {
            assertThat(called[0]).as("DeploymentInfoCustomizer should have been called").isTrue();
        }
        finally {
            webServer.stop();
        }
    }

    @Test
    void getWebServerReturnUndertowServletWebServerType() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        WebServer webServer = factory.getWebServer();
        webServer.start();
        try {
            assertThat(webServer).isInstanceOf(UndertowServletWebServer.class);
        }
        finally {
            webServer.stop();
        }
    }

    @Test
    void getWebServerStartsTheParsedListenerWithRandomPort() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        WebServer webServer = factory.getWebServer();
        webServer.start();
        try {
            assertThat(webServer.getPort()).isGreaterThan(0);
        }
        finally {
            webServer.stop();
        }
    }

    @Test
    void contextPathDefaultsToEmptyString() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        assertThat(factory.getContextPath()).isEmpty();
    }

    // ── End-to-end GET tests ──────────────────────────────────────────────────
    //
    // Start a real Undertow listener on a random port and drive it with the JDK
    // HttpClient. A raw HttpServlet is registered via ServletContextInitializer —
    // no Spring MVC, no DispatcherServlet — exercising the full stack:
    //   UndertowServletWebServerFactory → DeploymentManagerHttpHandlerFactory
    //   → Undertow 2.4 → Servlet 6.1 → HttpServlet.doGet()

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
