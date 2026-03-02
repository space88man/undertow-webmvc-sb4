package biz.example.web.tests;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.WebServerFactoryCustomizerBeanPostProcessor;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for integration testing of {@link ServletWebServerApplicationContext} and
 * {@link WebServer}s running Spring MVC.
 *
 * <p>Adapted from the Spring Boot internal
 * {@code org.springframework.boot.web.servlet.context.AbstractServletWebServerMvcIntegrationTests}
 * test fixture for use in this repository. The {@code @WithResource} annotation from
 * {@code spring-boot-test-support} is not available outside the Spring Boot build, so
 * {@code conf.properties} is provided as a permanent test classpath resource instead.
 *
 * @see UndertowServletWebServerMvcIntegrationTests
 */
public abstract class AbstractServletWebServerMvcIntegrationTests {

	private AnnotationConfigServletWebServerApplicationContext context;

	private final Class<?> webServerConfiguration;

	protected AbstractServletWebServerMvcIntegrationTests(Class<?> webServerConfiguration) {
		this.webServerConfiguration = webServerConfiguration;
	}

	@AfterEach
	void closeContext() {
		try {
			this.context.close();
		}
		catch (Exception ex) {
			// Ignore
		}
	}

	@Test
	void basicConfig() throws Exception {
		this.context = new AnnotationConfigServletWebServerApplicationContext(this.webServerConfiguration,
				Config.class);
		doTest(this.context, "/hello");
	}

	@Test
	void advancedConfig() throws Exception {
		this.context = new AnnotationConfigServletWebServerApplicationContext(this.webServerConfiguration,
				AdvancedConfig.class);
		doTest(this.context, "/example/spring/hello");
	}

	private void doTest(AnnotationConfigServletWebServerApplicationContext context, String resourcePath)
			throws Exception {
		SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
		ClientHttpRequest request = clientHttpRequestFactory.createRequest(
				new URI("http://localhost:" + context.getWebServer().getPort() + resourcePath), HttpMethod.GET);
		try (ClientHttpResponse response = request.execute()) {
			assertThat(response.getBody()).hasContent("Hello World");
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	static class Config {

		@Bean
		DispatcherServlet dispatcherServlet() {
			return new DispatcherServlet();
		}

		@Bean
		HelloWorldController helloWorldController() {
			return new HelloWorldController();
		}

	}

	/**
	 * Advanced configuration that reads {@code context=/example} from
	 * {@code classpath:conf.properties} (located at {@code src/test/resources/conf.properties})
	 * and sets it as the servlet context path, mounting the dispatcher under {@code /spring/*}.
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	@PropertySource("classpath:conf.properties")
	static class AdvancedConfig {

		private final Environment env;

		AdvancedConfig(Environment env) {
			this.env = env;
		}

		@Bean
		static WebServerFactoryCustomizerBeanPostProcessor webServerFactoryCustomizerBeanPostProcessor() {
			return new WebServerFactoryCustomizerBeanPostProcessor();
		}

		@Bean
		WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> contextPathCustomizer() {
			return (factory) -> {
				String contextPath = this.env.getProperty("context");
				factory.setContextPath(contextPath);
			};
		}

		@Bean
		ServletRegistrationBean<DispatcherServlet> dispatcherRegistration(DispatcherServlet dispatcherServlet) {
			ServletRegistrationBean<DispatcherServlet> registration = new ServletRegistrationBean<>(dispatcherServlet);
			registration.addUrlMappings("/spring/*");
			return registration;
		}

		@Bean
		DispatcherServlet dispatcherServlet() {
			return new DispatcherServlet();
		}

		@Bean
		HelloWorldController helloWorldController() {
			return new HelloWorldController();
		}

	}

	@Controller
	static class HelloWorldController {

		@RequestMapping("/hello")
		@ResponseBody
		String sayHello() {
			return "Hello World";
		}

	}

}
