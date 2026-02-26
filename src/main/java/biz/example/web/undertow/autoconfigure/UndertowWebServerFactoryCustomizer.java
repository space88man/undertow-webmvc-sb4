package biz.example.web.undertow.autoconfigure;

import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.undertow.UndertowOptions;
import org.xnio.Option;
import org.xnio.Options;

import biz.example.web.undertow.ConfigurableUndertowWebServerFactory;

import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.unit.DataSize;

/**
 * Customization for Undertow-specific features common for both Servlet and
 * Reactive
 * servers in Spring Boot 4.
 */
public class UndertowWebServerFactoryCustomizer
        implements WebServerFactoryCustomizer<ConfigurableUndertowWebServerFactory>, Ordered {

    private final Environment environment;

    private final ServerProperties serverProperties;

    private final UndertowServerProperties undertowProperties;

    public UndertowWebServerFactoryCustomizer(Environment environment, ServerProperties serverProperties,
            UndertowServerProperties undertowProperties) {
        this.environment = environment;
        this.serverProperties = serverProperties;
        this.undertowProperties = undertowProperties;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void customize(ConfigurableUndertowWebServerFactory factory) {
        PropertyMapper map = PropertyMapper.get(); // Correct: No global non-null toggle
        ServerOptions options = new ServerOptions(factory);

        map.from(this.serverProperties::getMaxHttpRequestHeaderSize)
                .when(this::isPositive)
                .asInt(DataSize::toBytes)
                .to(options.option(UndertowOptions.MAX_HEADER_SIZE));

        mapUndertowProperties(factory, options);
        mapAccessLogProperties(factory);

        factory.setUseForwardHeaders(getOrDeduceUseForwardHeaders());
    }

    private void mapUndertowProperties(ConfigurableUndertowWebServerFactory factory, ServerOptions serverOptions) {
        PropertyMapper map = PropertyMapper.get();
        UndertowServerProperties properties = this.undertowProperties;

        // Core Undertow settings
        map.from(properties::getBufferSize).asInt(DataSize::toBytes).to(factory::setBufferSize);
        map.from(properties::getDirectBuffers).to(factory::setUseDirectBuffers);

        // Threading (XNIO Worker)
        UndertowServerProperties.Threads threadProperties = properties.getThreads();
        map.from(threadProperties::getIo).to(factory::setIoThreads);
        map.from(threadProperties::getWorker).to(factory::setWorkerThreads);

        // Server Options
        map.from(properties::getMaxHttpPostSize)
                .when(this::isPositive)
                .as(DataSize::toBytes)
                .to(serverOptions.option(UndertowOptions.MAX_ENTITY_SIZE));

        map.from(properties::getMaxParameters).to(serverOptions.option(UndertowOptions.MAX_PARAMETERS));
        map.from(properties::getMaxHeaders).to(serverOptions.option(UndertowOptions.MAX_HEADERS));
        map.from(properties::getMaxCookies).to(serverOptions.option(UndertowOptions.MAX_COOKIES));

        // Decoders (Cleaned up: allowEncodedSlash removed)
        map.from(properties::getDecodeSlash).to(serverOptions.option(UndertowOptions.DECODE_SLASH));
        map.from(properties::isDecodeUrl).to(serverOptions.option(UndertowOptions.DECODE_URL));
        map.from(properties::getUrlCharset).as(Charset::name).to(serverOptions.option(UndertowOptions.URL_CHARSET));

        // Timeouts and Keep-alive
        map.from(properties::isAlwaysSetKeepAlive).to(serverOptions.option(UndertowOptions.ALWAYS_SET_KEEP_ALIVE));
        map.from(properties::getNoRequestTimeout)
                .asInt(Duration::toMillis)
                .to(serverOptions.option(UndertowOptions.NO_REQUEST_TIMEOUT));

        // Reflection-based escape hatches (Server and Socket)
        map.from(properties.getOptions()::getServer).to(serverOptions.forEach(serverOptions::option));
        SocketOptions socketOptions = new SocketOptions(factory);
        map.from(properties.getOptions()::getSocket).to(socketOptions.forEach(socketOptions::option));
    }

    private boolean isPositive(DataSize value) {
        return value != null && value.toBytes() > 0;
    }

    private void mapAccessLogProperties(ConfigurableUndertowWebServerFactory factory) {
        UndertowServerProperties.Accesslog properties = this.undertowProperties.getAccesslog();
        PropertyMapper map = PropertyMapper.get();

        map.from(properties::isEnabled).to((enabled) -> factory.setAccessLogEnabled(Boolean.TRUE.equals(enabled)));
        map.from(properties::getDir).to(factory::setAccessLogDirectory);
        map.from(properties::getPattern).to(factory::setAccessLogPattern);
        map.from(properties::getPrefix).to(factory::setAccessLogPrefix);
        map.from(properties::getSuffix).to(factory::setAccessLogSuffix);
        map.from(properties::isRotate).to((rotate) -> factory.setAccessLogRotate(Boolean.TRUE.equals(rotate)));
    }

    private boolean getOrDeduceUseForwardHeaders() {
        if (this.serverProperties.getForwardHeadersStrategy() == null) {
            CloudPlatform platform = CloudPlatform.getActive(this.environment);
            return platform != null && platform.isUsingForwardHeaders();
        }
        return this.serverProperties.getForwardHeadersStrategy().equals(ServerProperties.ForwardHeadersStrategy.NATIVE);
    }

    private abstract static class AbstractOptions {

        private final Class<?> source;
        private final Map<String, Option<?>> nameLookup;
        private final ConfigurableUndertowWebServerFactory factory;

        AbstractOptions(Class<?> source, ConfigurableUndertowWebServerFactory factory) {
            Map<String, Option<?>> lookup = new HashMap<>();
            ReflectionUtils.doWithLocalFields(source, (field) -> {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                        && Option.class.isAssignableFrom(field.getType())) {
                    try {
                        Option<?> option = (Option<?>) field.get(null);
                        lookup.put(getCanonicalName(field.getName()), option);
                    } catch (IllegalAccessException ex) {
                        // Ignore
                    }
                }
            });
            this.source = source;
            this.nameLookup = Collections.unmodifiableMap(lookup);
            this.factory = factory;
        }

        protected ConfigurableUndertowWebServerFactory getFactory() {
            return this.factory;
        }

        @SuppressWarnings("unchecked")
        <T> Consumer<Map<String, String>> forEach(Function<Option<T>, Consumer<T>> function) {
            return (map) -> map.forEach((key, value) -> {
                Option<T> option = (Option<T>) this.nameLookup.get(getCanonicalName(key));
                if (option == null) {
                    throw new IllegalStateException(
                            "Unable to find '" + key + "' in " + ClassUtils.getShortName(this.source));
                }
                T parsed = option.parseValue(value, getClass().getClassLoader());
                function.apply(option).accept(parsed);
            });
        }

        private static String getCanonicalName(String name) {
            StringBuilder canonicalName = new StringBuilder(name.length());
            name.chars()
                    .filter(Character::isLetterOrDigit)
                    .map(Character::toLowerCase)
                    .forEach((c) -> canonicalName.append((char) c));
            return canonicalName.toString();
        }

    }

    private static class ServerOptions extends AbstractOptions {
        ServerOptions(ConfigurableUndertowWebServerFactory factory) {
            super(UndertowOptions.class, factory);
        }

        <T> Consumer<T> option(Option<T> option) {
            return (value) -> getFactory().addBuilderCustomizers((builder) -> builder.setServerOption(option, value));
        }
    }

    private static class SocketOptions extends AbstractOptions {
        SocketOptions(ConfigurableUndertowWebServerFactory factory) {
            super(Options.class, factory);
        }

        <T> Consumer<T> option(Option<T> option) {
            return (value) -> getFactory().addBuilderCustomizers((builder) -> builder.setSocketOption(option, value));
        }
    }
}