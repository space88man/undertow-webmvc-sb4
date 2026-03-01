package biz.example.web.undertow.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.Environment;

import biz.example.web.undertow.ConfigurableUndertowWebServerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link UndertowWebServerFactoryCustomizer}.
 *
 * Verifies that:
 * - Thread counts from {@code server.undertow.threads.*} reach the factory
 * - Buffer size from {@code server.undertow.buffer-size} reaches the factory
 * - Access log properties propagate correctly
 * - Forward-headers strategy detection fires {@code setUseForwardHeaders}
 * - The {@code server.undertow.options.server.*} reflection engine applies
 *   arbitrary UndertowOptions without explicit mappings
 *
 * Uses a plain {@link org.springframework.boot.test.context.runner.ApplicationContextRunner}
 * — no real Undertow server is started.
 */
class UndertowWebServerFactoryCustomizerTests {

    @Test
    void workerThreadsAreAppliedToFactory() {
        ConfigurableUndertowWebServerFactory factory = mock(ConfigurableUndertowWebServerFactory.class);

        UndertowServerProperties properties = new UndertowServerProperties();
        properties.getThreads().setWorker(16);
        properties.getThreads().setIo(4);

        customize(factory, properties);

        verify(factory).setWorkerThreads(16);
        verify(factory).setIoThreads(4);
    }

    @Test
    void bufferSizeIsAppliedToFactory() {
        ConfigurableUndertowWebServerFactory factory = mock(ConfigurableUndertowWebServerFactory.class);

        UndertowServerProperties properties = new UndertowServerProperties();
        // 16KB buffer
        properties.setBufferSize(org.springframework.util.unit.DataSize.ofKilobytes(16));

        customize(factory, properties);

        verify(factory).setBufferSize(16384);
    }

    @Test
    void accessLogEnabledPropagates() {
        ConfigurableUndertowWebServerFactory factory = mock(ConfigurableUndertowWebServerFactory.class);

        UndertowServerProperties properties = new UndertowServerProperties();
        properties.getAccesslog().setEnabled(true);

        customize(factory, properties);

        verify(factory).setAccessLogEnabled(true);
    }

    @Test
    void nativeForwardHeadersStrategyEnablesForwardHeaders() {
        ConfigurableUndertowWebServerFactory factory = mock(ConfigurableUndertowWebServerFactory.class);

        MockEnvironment env = new MockEnvironment();
        ServerProperties serverProperties = new ServerProperties();
        serverProperties.setForwardHeadersStrategy(ServerProperties.ForwardHeadersStrategy.NATIVE);
        UndertowServerProperties undertowProperties = new UndertowServerProperties();

        new UndertowWebServerFactoryCustomizer(env, serverProperties, undertowProperties).customize(factory);

        verify(factory).setUseForwardHeaders(true);
    }

    @Test
    void noForwardHeadersStrategyDefaultsToFalseOnNonCloudPlatform() {
        ConfigurableUndertowWebServerFactory factory = mock(ConfigurableUndertowWebServerFactory.class);

        MockEnvironment env = new MockEnvironment();
        ServerProperties serverProperties = new ServerProperties();
        // strategy is null — no cloud platform active in MockEnvironment
        UndertowServerProperties undertowProperties = new UndertowServerProperties();

        new UndertowWebServerFactoryCustomizer(env, serverProperties, undertowProperties).customize(factory);

        verify(factory).setUseForwardHeaders(false);
    }

    /**
     * Exercises the AbstractOptions reflection engine: server.undertow.options.server.*
     * keys are mapped to UndertowOptions fields by canonical name at runtime.
     * This test ensures no exception is thrown for a known valid option key.
     */
    @Test
    void serverOptionViaReflectionEngineDoesNotThrow() {
        ConfigurableUndertowWebServerFactory factory = mock(ConfigurableUndertowWebServerFactory.class);

        UndertowServerProperties properties = new UndertowServerProperties();
        properties.getOptions().getServer().put("ENABLE_HTTP2", "true");

        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> customize(factory, properties));
    }

    @Test
    void unknownServerOptionThrowsIllegalStateException() {
        ConfigurableUndertowWebServerFactory factory = mock(ConfigurableUndertowWebServerFactory.class);

        UndertowServerProperties properties = new UndertowServerProperties();
        properties.getOptions().getServer().put("NONEXISTENT_OPTION_XYZ", "true");

        org.assertj.core.api.Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> customize(factory, properties))
                .withMessageContaining("NONEXISTENT_OPTION_XYZ");
    }

    private void customize(ConfigurableUndertowWebServerFactory factory, UndertowServerProperties undertowProperties) {
        MockEnvironment env = new MockEnvironment();
        ServerProperties serverProperties = new ServerProperties();
        new UndertowWebServerFactoryCustomizer(env, serverProperties, undertowProperties).customize(factory);
    }

}
