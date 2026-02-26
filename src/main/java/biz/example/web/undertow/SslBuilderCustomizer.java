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

import java.net.InetAddress;
import javax.net.ssl.SSLContext;

import io.undertow.Undertow;
import org.jspecify.annotations.Nullable;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.web.server.Ssl;

/**
 * {@link UndertowBuilderCustomizer} that configures SSL on an Undertow
 * {@link Undertow.Builder}.
 *
 * @author Andy Wilkinson
 * @author Scott Frederick
 */
class SslBuilderCustomizer implements UndertowBuilderCustomizer {

	private final int port;

	private final @Nullable InetAddress address;

	private final Ssl.@Nullable ClientAuth clientAuth;

	private final SslBundle sslBundle;

	SslBuilderCustomizer(int port, @Nullable InetAddress address,
			Ssl.@Nullable ClientAuth clientAuth, SslBundle sslBundle) {
		this.port = port;
		this.address = address;
		this.clientAuth = clientAuth;
		this.sslBundle = sslBundle;
	}

	@Override
	public void customize(Undertow.Builder builder) {
		try {
			SSLContext sslContext = this.sslBundle.createSslContext();
			String host = (this.address != null) ? this.address.getHostAddress() : "0.0.0.0";
			builder.addHttpsListener(this.port, host, sslContext);
			configureClientAuth(builder);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to configure Undertow SSL", ex);
		}
	}

	private void configureClientAuth(Undertow.Builder builder) {
		if (this.clientAuth == Ssl.ClientAuth.NEED) {
			builder.setSocketOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUIRED);
		}
		else if (this.clientAuth == Ssl.ClientAuth.WANT) {
			builder.setSocketOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED);
		}
	}

}
