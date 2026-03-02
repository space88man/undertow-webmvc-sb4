package biz.example.web.undertow;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

import io.undertow.Undertow;
import org.junit.jupiter.api.Test;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.boot.web.server.Ssl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link SslBuilderCustomizer}.
 *
 * <p>Each test starts a real Undertow HTTPS listener on port 0 and drives it
 * with the JDK {@link HttpClient}. This exercises the full SSL handshake path:
 * <pre>
 *   SslBuilderCustomizer.customize(builder)
 *     → Undertow.addHttpsListener()
 *     → XNIO SSLContext negotiation
 *     → HTTPS response
 * </pre>
 *
 * Test fixtures live under {@code src/test/resources/ssl/}:
 * <ul>
 *   <li>{@code ssl/ca/ca.crt}               — CA that signed all certs</li>
 *   <li>{@code ssl/server/server.crt+key}   — server identity</li>
 *   <li>{@code ssl/client/client.crt+key}   — valid client identity</li>
 *   <li>{@code ssl/client-expired/}         — expired client cert</li>
 * </ul>
 */
class SslBuilderCustomizerTests {

	// ── SSL bundle factories ─────────────────────────────────────────────────

	/** SslBundle loaded with the server cert/key (keystore) + CA trust. */
	private static SslBundle serverSslBundle() {
		PemSslStoreDetails keyStore = PemSslStoreDetails
				.forCertificate("classpath:ssl/server/server.crt")
				.withPrivateKey("classpath:ssl/server/server.key");
		PemSslStoreDetails trustStore = PemSslStoreDetails.forCertificate("classpath:ssl/ca/ca.crt");
		return SslBundle.of(new PemSslStoreBundle(keyStore, trustStore));
	}

	/** SSLContext for a plain HTTPS client — trusts only the test CA, no client cert. */
	private static SSLContext clientSslContextTrustingCa() throws Exception {
		PemSslStoreDetails trustStore = PemSslStoreDetails.forCertificate("classpath:ssl/ca/ca.crt");
		return SslBundle.of(new PemSslStoreBundle(null, trustStore)).createSslContext();
	}

	/** SSLContext for a mutual-TLS client — presents the test client cert. */
	private static SSLContext clientSslContextWithClientCert() throws Exception {
		PemSslStoreDetails keyStore = PemSslStoreDetails
				.forCertificate("classpath:ssl/client/client.crt")
				.withPrivateKey("classpath:ssl/client/client.key");
		PemSslStoreDetails trustStore = PemSslStoreDetails.forCertificate("classpath:ssl/ca/ca.crt");
		SslStoreBundle bundle = new PemSslStoreBundle(keyStore, trustStore);
		return SslBundle.of(bundle).createSslContext();
	}

	// ── Undertow raw-builder helpers ─────────────────────────────────────────

	/**
	 * Build and start an Undertow server with the given customizer applied.
	 * The handler replies {@code 200 OK} with body {@code "OK"}.
	 */
	private static Undertow startUndertow(SslBuilderCustomizer customizer) {
		Undertow.Builder builder = Undertow.builder();
		customizer.customize(builder);
		builder.setHandler((exchange) -> exchange.getResponseSender().send("OK"));
		Undertow undertow = builder.build();
		undertow.start();
		return undertow;
	}

	/** Return the bound port from the first listener of a started {@link Undertow}. */
	private static int portOf(Undertow undertow) {
		return ((InetSocketAddress) undertow.getListenerInfo().get(0).getAddress()).getPort();
	}

	// ── Tests ────────────────────────────────────────────────────────────────

	@Test
	void customizeAddsHttpsListenerAndServerResponds() throws Exception {
		SslBuilderCustomizer customizer = new SslBuilderCustomizer(0, null, null, serverSslBundle());
		Undertow undertow = startUndertow(customizer);
		try {
			int port = portOf(undertow);
			HttpClient client = HttpClient.newBuilder()
					.sslContext(clientSslContextTrustingCa())
					.build();
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder()
							.uri(URI.create("https://localhost:" + port + "/"))
							.GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).isEqualTo("OK");
		}
		finally {
			undertow.stop();
		}
	}

	@Test
	void customizeWithNullAddressBindsToAllInterfaces() throws Exception {
		// null address → "0.0.0.0" wildcard — server should accept loopback connections
		SslBuilderCustomizer customizer = new SslBuilderCustomizer(0, null, null, serverSslBundle());
		Undertow undertow = startUndertow(customizer);
		try {
			int port = portOf(undertow);
			HttpClient client = HttpClient.newBuilder()
					.sslContext(clientSslContextTrustingCa())
					.build();
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder()
							.uri(URI.create("https://127.0.0.1:" + port + "/"))
							.GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
		}
		finally {
			undertow.stop();
		}
	}

	@Test
	void customizeWithLoopbackAddressBindsToLoopback() throws Exception {
		InetAddress loopback = InetAddress.getLoopbackAddress();
		SslBuilderCustomizer customizer = new SslBuilderCustomizer(0, loopback, null, serverSslBundle());
		Undertow undertow = startUndertow(customizer);
		try {
			int port = portOf(undertow);
			HttpClient client = HttpClient.newBuilder()
					.sslContext(clientSslContextTrustingCa())
					.build();
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder()
							.uri(URI.create("https://127.0.0.1:" + port + "/"))
							.GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
		}
		finally {
			undertow.stop();
		}
	}

	@Test
	void clientAuthNoneAllowsUnauthenticatedClient() throws Exception {
		// no ClientAuth → default, unauthenticated client must succeed
		SslBuilderCustomizer customizer = new SslBuilderCustomizer(0, null, Ssl.ClientAuth.NONE, serverSslBundle());
		Undertow undertow = startUndertow(customizer);
		try {
			int port = portOf(undertow);
			HttpClient client = HttpClient.newBuilder()
					.sslContext(clientSslContextTrustingCa())
					.build();
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder()
							.uri(URI.create("https://localhost:" + port + "/"))
							.GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
		}
		finally {
			undertow.stop();
		}
	}

	@Test
	void clientAuthWantAllowsClientWithNoCert() throws Exception {
		// WANT → server requests cert but does not require it; plain client succeeds
		SslBuilderCustomizer customizer = new SslBuilderCustomizer(0, null, Ssl.ClientAuth.WANT, serverSslBundle());
		Undertow undertow = startUndertow(customizer);
		try {
			int port = portOf(undertow);
			HttpClient client = HttpClient.newBuilder()
					.sslContext(clientSslContextTrustingCa())
					.build();
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder()
							.uri(URI.create("https://localhost:" + port + "/"))
							.GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
		}
		finally {
			undertow.stop();
		}
	}

	@Test
	void clientAuthNeedRejectsUnauthenticatedClient() throws Exception {
		// NEED → unauthenticated client must fail the SSL handshake
		SslBuilderCustomizer customizer = new SslBuilderCustomizer(0, null, Ssl.ClientAuth.NEED, serverSslBundle());
		Undertow undertow = startUndertow(customizer);
		try {
			int port = portOf(undertow);
			HttpClient client = HttpClient.newBuilder()
					.sslContext(clientSslContextTrustingCa())
					.build();
			assertThatExceptionOfType(Exception.class)
					.isThrownBy(() -> client.send(
							HttpRequest.newBuilder()
									.uri(URI.create("https://localhost:" + port + "/"))
									.GET().build(),
							HttpResponse.BodyHandlers.ofString()))
					.withCauseInstanceOf(SSLHandshakeException.class);
		}
		finally {
			undertow.stop();
		}
	}

	@Test
	void clientAuthNeedAcceptsMutualTlsClient() throws Exception {
		// NEED → client presenting a valid CA-signed cert must succeed
		SslBuilderCustomizer customizer = new SslBuilderCustomizer(0, null, Ssl.ClientAuth.NEED, serverSslBundle());
		Undertow undertow = startUndertow(customizer);
		try {
			int port = portOf(undertow);
			HttpClient client = HttpClient.newBuilder()
					.sslContext(clientSslContextWithClientCert())
					.build();
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder()
							.uri(URI.create("https://localhost:" + port + "/"))
							.GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
		}
		finally {
			undertow.stop();
		}
	}

	@Test
	void invalidSslBundleThrowsIllegalStateException() {
		// SslBundle whose createSslContext() fails must surface as IllegalStateException
		SslBundle badBundle = createBrokenSslBundle();
		SslBuilderCustomizer customizer = new SslBuilderCustomizer(0, null, null, badBundle);
		assertThatExceptionOfType(IllegalStateException.class)
				.isThrownBy(() -> customizer.customize(Undertow.builder()))
				.withMessage("Failed to configure Undertow SSL");
	}

	/** Produces an {@link SslBundle} whose {@link SslBundle#createSslContext()} always throws. */
	private static SslBundle createBrokenSslBundle() {
		return new SslBundle() {
			@Override
			public org.springframework.boot.ssl.SslStoreBundle getStores() {
				return SslStoreBundle.NONE;
			}

			@Override
			public org.springframework.boot.ssl.SslBundleKey getKey() {
				return org.springframework.boot.ssl.SslBundleKey.NONE;
			}

			@Override
			public org.springframework.boot.ssl.SslOptions getOptions() {
				return org.springframework.boot.ssl.SslOptions.NONE;
			}

			@Override
			public String getProtocol() {
				return "TLS";
			}

			@Override
			public org.springframework.boot.ssl.SslManagerBundle getManagers() {
				return null;
			}

			@Override
			public SSLContext createSslContext() {
				throw new RuntimeException("deliberate failure in test");
			}
		};
	}

}
