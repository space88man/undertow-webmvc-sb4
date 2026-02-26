/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.example.web.undertow;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.GracefulShutdownHandler;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.web.server.AbstractConfigurableWebServerFactory;
import org.springframework.boot.web.server.Http2;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.Ssl;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Base class for factories that produce an {@link UndertowWebServer}.
 *
 * @author Andy Wilkinson
 * @since 4.0.0
 */
public class UndertowWebServerFactory extends AbstractConfigurableWebServerFactory
		implements ConfigurableUndertowWebServerFactory {

	private Set<UndertowBuilderCustomizer> builderCustomizers = new LinkedHashSet<>();

	private @Nullable Integer bufferSize;

	private @Nullable Integer ioThreads;

	private @Nullable Integer workerThreads;

	private @Nullable Boolean directBuffers;

	private @Nullable File accessLogDirectory;

	private @Nullable String accessLogPattern;

	private @Nullable String accessLogPrefix;

	private @Nullable String accessLogSuffix;

	private boolean accessLogEnabled = false;

	private boolean accessLogRotate = true;

	private boolean useForwardHeaders;

	protected UndertowWebServerFactory() {
	}

	protected UndertowWebServerFactory(int port) {
		super(port);
	}

	/**
	 * Returns a mutable collection of the {@link UndertowBuilderCustomizer}s that will
	 * be applied to the Undertow {@link io.undertow.Undertow.Builder}.
	 * @return the builder customizers
	 */
	public Collection<UndertowBuilderCustomizer> getBuilderCustomizers() {
		return this.builderCustomizers;
	}

	/**
	 * Set {@link UndertowBuilderCustomizer}s that should be applied to the Undertow
	 * {@link io.undertow.Undertow.Builder}. Calling this method will replace any
	 * existing customizers.
	 * @param customizers the customizers to set
	 */
	public void setBuilderCustomizers(Collection<? extends UndertowBuilderCustomizer> customizers) {
		Assert.notNull(customizers, "'customizers' must not be null");
		this.builderCustomizers = new LinkedHashSet<>(customizers);
	}

	@Override
	public void addBuilderCustomizers(UndertowBuilderCustomizer... customizers) {
		Assert.notNull(customizers, "'customizers' must not be null");
		this.builderCustomizers.addAll(Arrays.asList(customizers));
	}

	@Override
	public void setBufferSize(@Nullable Integer bufferSize) {
		this.bufferSize = bufferSize;
	}

	public @Nullable Integer getBufferSize() {
		return this.bufferSize;
	}

	@Override
	public void setIoThreads(@Nullable Integer ioThreads) {
		this.ioThreads = ioThreads;
	}

	public @Nullable Integer getIoThreads() {
		return this.ioThreads;
	}

	@Override
	public void setWorkerThreads(@Nullable Integer workerThreads) {
		this.workerThreads = workerThreads;
	}

	public @Nullable Integer getWorkerThreads() {
		return this.workerThreads;
	}

	@Override
	public void setUseDirectBuffers(@Nullable Boolean directBuffers) {
		this.directBuffers = directBuffers;
	}

	public @Nullable Boolean getDirectBuffers() {
		return this.directBuffers;
	}

	@Override
	public void setAccessLogEnabled(boolean accessLogEnabled) {
		this.accessLogEnabled = accessLogEnabled;
	}

	public boolean isAccessLogEnabled() {
		return this.accessLogEnabled;
	}

	@Override
	public void setAccessLogDirectory(@Nullable File accessLogDirectory) {
		this.accessLogDirectory = accessLogDirectory;
	}

	public @Nullable File getAccessLogDirectory() {
		return this.accessLogDirectory;
	}

	@Override
	public void setAccessLogPattern(@Nullable String accessLogPattern) {
		this.accessLogPattern = accessLogPattern;
	}

	public @Nullable String getAccessLogPattern() {
		return this.accessLogPattern;
	}

	@Override
	public void setAccessLogPrefix(@Nullable String accessLogPrefix) {
		this.accessLogPrefix = accessLogPrefix;
	}

	public @Nullable String getAccessLogPrefix() {
		return this.accessLogPrefix;
	}

	@Override
	public void setAccessLogSuffix(@Nullable String accessLogSuffix) {
		this.accessLogSuffix = accessLogSuffix;
	}

	public @Nullable String getAccessLogSuffix() {
		return this.accessLogSuffix;
	}

	@Override
	public void setAccessLogRotate(boolean accessLogRotate) {
		this.accessLogRotate = accessLogRotate;
	}

	public boolean isAccessLogRotate() {
		return this.accessLogRotate;
	}

	@Override
	public void setUseForwardHeaders(boolean useForwardHeaders) {
		this.useForwardHeaders = useForwardHeaders;
	}

	public boolean isUseForwardHeaders() {
		return this.useForwardHeaders;
	}

	/**
	 * Create an {@link Undertow.Builder} configured for this factory's settings.
	 * @return a configured builder
	 */
	protected Undertow.Builder createBuilder() {
		InetAddress address = getAddress();
		int port = getPort();
		Undertow.Builder builder = Undertow.builder();
		if (this.bufferSize != null) {
			builder.setBufferSize(this.bufferSize);
		}
		if (this.ioThreads != null) {
			builder.setIoThreads(this.ioThreads);
		}
		if (this.workerThreads != null) {
			builder.setWorkerThreads(this.workerThreads);
		}
		if (this.directBuffers != null) {
			builder.setDirectBuffers(this.directBuffers);
		}
		Http2 http2 = getHttp2();
		if (http2 != null) {
			builder.setServerOption(UndertowOptions.ENABLE_HTTP2, http2.isEnabled());
		}
		Ssl ssl = getSsl();
		if (Ssl.isEnabled(ssl)) {
			new SslBuilderCustomizer(port, address, ssl.getClientAuth(), getSslBundle()).customize(builder);
		}
		else {
			builder.addHttpListener(port, (address != null) ? address.getHostAddress() : "0.0.0.0");
		}
		builder.setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 0);
		for (UndertowBuilderCustomizer customizer : this.builderCustomizers) {
			customizer.customize(builder);
		}
		return builder;
	}

	/**
	 * Create the {@link UndertowWebServer.HttpHandlerFactory} chain for this factory,
	 * prepending the given initial factories.
	 * @param initialHttpHandlerFactories factories to prepend to the chain
	 * @return the complete list of handler factories
	 */
	protected List<UndertowWebServer.HttpHandlerFactory> createHttpHandlerFactories(
			UndertowWebServer.HttpHandlerFactory... initialHttpHandlerFactories) {
		List<UndertowWebServer.HttpHandlerFactory> factories = new ArrayList<>(
				Arrays.asList(initialHttpHandlerFactories));
		if (getCompression() != null && getCompression().getEnabled()) {
			factories.add(new CompressionHttpHandlerFactory(getCompression()));
		}
		if (this.useForwardHeaders) {
			factories.add(Handlers::proxyPeerAddress);
		}
		if (StringUtils.hasText(getServerHeader())) {
			factories.add((next) -> Handlers.header(next, "Server", getServerHeader()));
		}
		if (getShutdown() == Shutdown.GRACEFUL) {
			factories.add(this::createGracefulShutdownHandler);
		}
		if (this.accessLogEnabled) {
			factories.add(new AccessLogHttpHandlerFactory(this.accessLogDirectory, this.accessLogPattern,
					this.accessLogPrefix, this.accessLogSuffix, this.accessLogRotate));
		}
		return factories;
	}

	private UndertowWebServer.GracefulShutdownHttpHandler createGracefulShutdownHandler(HttpHandler next) {
		GracefulShutdownHandler delegate = Handlers.gracefulShutdown(next);
		return new UndertowWebServer.GracefulShutdownHttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception {
				delegate.handleRequest(exchange);
			}

			@Override
			public void shutdown() {
				delegate.shutdown();
			}

			@Override
			public boolean awaitShutdown(long timeout) throws InterruptedException {
				return delegate.awaitShutdown(timeout);
			}
		};
	}

}
