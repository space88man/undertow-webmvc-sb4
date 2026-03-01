package biz.example.web.undertow.autoconfigure;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link UndertowServerProperties}.
 *
 * Verifies that:
 * - Default field values match Undertow's own defaults (from UndertowOptions constants)
 * - Spring Boot property binding maps {@code server.undertow.*} keys correctly
 * - Nested sections (accesslog, threads, options) bind independently
 *
 * These are plain unit tests — no Spring application context is required because
 * {@link UndertowServerProperties} is a simple POJO bound via Spring's
 * {@link Binder} API directly.
 */
class UndertowServerPropertiesTests {

    @Test
    void defaultValuesMatchUndertowDefaults() {
        UndertowServerProperties properties = new UndertowServerProperties();
        assertThat(properties.isEagerFilterInit()).isTrue();
        assertThat(properties.isDecodeUrl()).isTrue();
        assertThat(properties.getUrlCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(properties.isAlwaysSetKeepAlive()).isTrue();
        assertThat(properties.isPreservePathOnForward()).isFalse();
        assertThat(properties.getNoRequestTimeout()).isNull();
    }

    @Test
    void accesslogDefaultsAreCorrect() {
        UndertowServerProperties.Accesslog accesslog = new UndertowServerProperties().getAccesslog();
        assertThat(accesslog.isEnabled()).isFalse();
        assertThat(accesslog.getPattern()).isEqualTo("common");
        assertThat(accesslog.getPrefix()).isEqualTo("access_log.");
        assertThat(accesslog.getSuffix()).isEqualTo("log");
        assertThat(accesslog.isRotate()).isTrue();
    }

    @Test
    void threadDefaultsHaveNullValues() {
        // null means Undertow will apply its own CPU-derived defaults
        UndertowServerProperties.Threads threads = new UndertowServerProperties().getThreads();
        assertThat(threads.getIo()).isNull();
        assertThat(threads.getWorker()).isNull();
    }

    @Test
    void optionMapsAreInitiallyEmpty() {
        UndertowServerProperties.Options options = new UndertowServerProperties().getOptions();
        assertThat(options.getServer()).isEmpty();
        assertThat(options.getSocket()).isEmpty();
    }

    @Test
    void propertiesBindFromEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("server.undertow.threads.worker", "16");
        env.setProperty("server.undertow.threads.io", "4");
        env.setProperty("server.undertow.eager-filter-init", "false");
        env.setProperty("server.undertow.accesslog.enabled", "true");
        env.setProperty("server.undertow.accesslog.dir", "/var/log/app");

        UndertowServerProperties bound = bindProperties(env);

        assertThat(bound.getThreads().getWorker()).isEqualTo(16);
        assertThat(bound.getThreads().getIo()).isEqualTo(4);
        assertThat(bound.isEagerFilterInit()).isFalse();
        assertThat(bound.getAccesslog().isEnabled()).isTrue();
        assertThat(bound.getAccesslog().getDir()).isEqualTo(new File("/var/log/app"));
    }

    @Test
    void optionMapsBindFromEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("server.undertow.options.server.ENABLE_HTTP2", "true");
        env.setProperty("server.undertow.options.socket.TCP_NODELAY", "true");

        UndertowServerProperties bound = bindProperties(env);

        assertThat(bound.getOptions().getServer()).containsEntry("ENABLE_HTTP2", "true");
        assertThat(bound.getOptions().getSocket()).containsEntry("TCP_NODELAY", "true");
    }

    private UndertowServerProperties bindProperties(MockEnvironment env) {
        Binder binder = new Binder(ConfigurationPropertySources.get(env));
        return binder.bind("server.undertow", Bindable.of(UndertowServerProperties.class))
                .orElseGet(UndertowServerProperties::new);
    }

}
