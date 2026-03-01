package biz.example.web.undertow.it;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import biz.example.web.undertow.servlet.UndertowServletWebServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full Spring Boot application integration test for the Undertow servlet stack.
 *
 * <p>Starts a complete {@link SpringBootApplication} on a random port — Spring MVC
 * autoconfiguration, {@code DispatcherServlet}, and our
 * {@code UndertowServletWebServerAutoConfiguration} are all loaded via the standard
 * {@code AutoConfiguration.imports} mechanism, which is exactly what a real
 * application would exercise.
 *
 * <p>Unlike the {@code ApplicationContextRunner}-based unit tests, this test verifies
 * the full assembled path:
 * <pre>
 *   @SpringBootApplication
 *     → AutoConfiguration.imports scan
 *     → UndertowServletWebServerAutoConfiguration
 *     → UndertowServletWebServerFactory
 *     → UndertowServletWebServer (XNIO listener on random port)
 *     → Spring MVC DispatcherServlet
 *     → @RestController
 * </pre>
 */
@SpringBootTest(classes = UndertowServletApplicationIT.TestApplication.class,
		webEnvironment = WebEnvironment.RANDOM_PORT)
class UndertowServletApplicationIT {

	@LocalServerPort
	int port;

	@Autowired
	WebServerApplicationContext applicationContext;

	// ── Tests ─────────────────────────────────────────────────────────────────

	@Test
	void helloEndpointReturns200WithExpectedBody() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder()
						.uri(URI.create("http://localhost:" + port + "/hello"))
						.GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("Hello from Undertow");
	}

	@Test
	void unknownPathReturns404() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder()
						.uri(URI.create("http://localhost:" + port + "/not-here"))
						.GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(404);
	}

	@Test
	void embeddedServerIsUndertow() {
		WebServer webServer = applicationContext.getWebServer();
		assertThat(webServer)
				.as("embedded server must be UndertowServletWebServer, not Tomcat or Jetty")
				.isInstanceOf(UndertowServletWebServer.class);
	}

	// ── Application ───────────────────────────────────────────────────────────

	/**
	 * Minimal Spring Boot application that triggers the full autoconfiguration scan.
	 * {@code @SpringBootApplication} causes Spring Boot to read
	 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
	 * which is how {@code UndertowServletWebServerAutoConfiguration} is discovered —
	 * the same path a real deployed application would use.
	 *
	 * <p>{@code HelloController} is explicitly imported because Spring Boot's
	 * {@code TypeExcludeFilter} prevents component-scanning of inner classes
	 * declared inside a {@code @SpringBootTest} class.
	 */
	@SpringBootApplication
	@Import(HelloController.class)
	static class TestApplication {
	}

	// ── Controller ────────────────────────────────────────────────────────────

	@RestController
	static class HelloController {

		@GetMapping("/hello")
		String hello() {
			return "Hello from Undertow";
		}

	}

}
