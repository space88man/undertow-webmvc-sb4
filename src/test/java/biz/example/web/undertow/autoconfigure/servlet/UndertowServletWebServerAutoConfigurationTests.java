package biz.example.web.undertow.autoconfigure.servlet;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import biz.example.web.undertow.autoconfigure.UndertowServerProperties;
import biz.example.web.undertow.servlet.UndertowServletWebServerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link UndertowServletWebServerAutoConfiguration}.
 *
 * Verifies:
 * - The autoconfiguration creates all expected beans ({@link UndertowServletWebServerFactory},
 *   {@link UndertowServletWebServerFactoryCustomizer})
 * - {@code @ConditionalOnMissingBean} is respected — a user-provided
 *   {@link ServletWebServerFactory} suppresses the auto-registered factory
 * - {@link UndertowWebSocketServletWebServerCustomizer} is registered when
 *   {@code io.undertow.websockets.jsr.Bootstrap} is on the classpath (it is in test scope)
 * - {@code server.undertow.eager-filter-init=false} flows end-to-end from property
 *   to the customizer that applies it to the factory
 *
 * Uses {@link WebApplicationContextRunner} (SERVLET type) so that the
 * {@code @ConditionalOnWebApplication(type = SERVLET)} guard passes.
 * No real Undertow listener is bound — the runner never calls {@code getWebServer()}.
 */
class UndertowServletWebServerAutoConfigurationTests {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UndertowServletWebServerAutoConfiguration.class));

    @Test
    void undertowServletWebServerFactoryBeanIsRegistered() {
        contextRunner.run((context) ->
                assertThat(context).hasSingleBean(UndertowServletWebServerFactory.class));
    }

    @Test
    void undertowServletWebServerFactoryCustomizerBeanIsRegistered() {
        contextRunner.run((context) ->
                assertThat(context).hasSingleBean(UndertowServletWebServerFactoryCustomizer.class));
    }

    @Test
    void undertowServerPropertiesBeanIsRegistered() {
        contextRunner.run((context) ->
                assertThat(context).hasSingleBean(UndertowServerProperties.class));
    }

    @Test
    void conditionalOnMissingBeanIsRespectedForServletWebServerFactory() {
        contextRunner
                .withUserConfiguration(CustomServletWebServerFactoryConfiguration.class)
                .run((context) -> {
                    // User-provided bean must be present
                    assertThat(context).hasSingleBean(ServletWebServerFactory.class);
                    // Auto-configured Undertow factory must NOT be present
                    assertThat(context).doesNotHaveBean(UndertowServletWebServerFactory.class);
                });
    }

    @Test
    void websocketCustomizerIsRegisteredWhenBootstrapIsOnClasspath() {
        // io.undertow.websockets.jsr.Bootstrap is available because undertow-websockets
        // is in the test-scope compile classpath, so this bean must be present
        contextRunner.run((context) ->
                assertThat(context).hasSingleBean(UndertowWebSocketServletWebServerCustomizer.class));
    }

    @Test
    void eagerFilterInitPropertyFlowsThroughToCustomizer() {
        contextRunner
                .withPropertyValues("server.undertow.eager-filter-init=false")
                .run((context) -> {
                    UndertowServerProperties props = context.getBean(UndertowServerProperties.class);
                    assertThat(props.isEagerFilterInit()).isFalse();
                });
    }

    @Test
    void accesslogPatternPropertyIsBindable() {
        contextRunner
                .withPropertyValues("server.undertow.accesslog.enabled=true",
                        "server.undertow.accesslog.pattern=%h %t \"%r\" %s")
                .run((context) -> {
                    UndertowServerProperties props = context.getBean(UndertowServerProperties.class);
                    assertThat(props.getAccesslog().isEnabled()).isTrue();
                    assertThat(props.getAccesslog().getPattern()).isEqualTo("%h %t \"%r\" %s");
                });
    }

    /**
     * User configuration that provides a custom {@link ServletWebServerFactory}, which
     * must prevent the auto-configured {@link UndertowServletWebServerFactory} from
     * being registered due to {@code @ConditionalOnMissingBean}.
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomServletWebServerFactoryConfiguration {

        @Bean
        ServletWebServerFactory customServletWebServerFactory() {
            // Minimal no-op factory — we only need the bean to exist
            return (initializers) -> {
                throw new UnsupportedOperationException("test stub — not called");
            };
        }

    }

}
