package biz.example.web.undertow;

import biz.example.web.tests.AbstractServletWebServerMvcIntegrationTests;
import biz.example.web.undertow.servlet.UndertowServletWebServerFactory;

import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Integration tests for {@link ServletWebServerApplicationContext} and
 * {@link UndertowServletWebServerFactory} running Spring MVC.
 */
class UndertowServletWebServerMvcIntegrationTests extends AbstractServletWebServerMvcIntegrationTests {

	UndertowServletWebServerMvcIntegrationTests() {
		super(UndertowConfig.class);
	}

	@Configuration(proxyBeanMethods = false)
	static class UndertowConfig {

		@Bean
		UndertowServletWebServerFactory webServerFactory() {
			return new UndertowServletWebServerFactory(0);
		}

	}

}
