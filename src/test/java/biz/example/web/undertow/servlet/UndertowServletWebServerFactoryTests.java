package biz.example.web.undertow.servlet;

import java.net.ServerSocket;

import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.PortInUseException;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.servlet.ServletWebServerSettings;

import biz.example.web.undertow.UndertowBuilderCustomizer;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UndertowServletWebServerFactory}.
 *
 * Validates factory construction and default state — no real Undertow server is
 * started. For end-to-end HTTP tests see {@link UndertowServletWebServerIntegrationTests}.
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

    @Test
    void getPortReturnsMinusOneBeforeStart() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        WebServer server = factory.getWebServer();
        // channels are empty before start() — contract: -1 means not yet bound
        assertThat(server.getPort()).isEqualTo(-1);
        server.start();
        try {
            assertThat(server.getPort()).isGreaterThan(0);
        }
        finally {
            server.stop();
        }
    }

    @Test
    void stopOnAlreadyStoppedServerDoesNotThrow() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        WebServer server = factory.getWebServer();
        server.start();
        server.stop();
        // second stop must be a no-op, not an exception
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(server::stop);
    }

    @Test
    void portInUseExceptionWhenPortAlreadyBound() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            int port = occupied.getLocalPort();
            UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(port);
            WebServer server = factory.getWebServer();
            assertThatExceptionOfType(PortInUseException.class)
                    .isThrownBy(server::start);
        }
    }

    @Test
    void builderCustomizerIsInvokedDuringGetWebServer() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);

        boolean[] called = { false };
        factory.addBuilderCustomizers((UndertowBuilderCustomizer) (builder) -> called[0] = true);

        WebServer server = factory.getWebServer();
        server.start();
        try {
            assertThat(called[0]).as("UndertowBuilderCustomizer should have been called").isTrue();
        }
        finally {
            server.stop();
        }
    }

}
