package biz.example.web.undertow.servlet;

import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.servlet.ServletWebServerSettings;

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

}
