package biz.example.web.undertow.autoconfigure.servlet;

import jakarta.servlet.Servlet;
import io.undertow.Undertow;
import org.xnio.SslClientAuthMode;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.autoconfigure.servlet.ServletWebServerConfiguration;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import biz.example.web.undertow.UndertowBuilderCustomizer;
import biz.example.web.undertow.UndertowDeploymentInfoCustomizer;
import biz.example.web.undertow.autoconfigure.UndertowServerProperties;
import biz.example.web.undertow.autoconfigure.UndertowWebServerConfiguration;
import biz.example.web.undertow.servlet.UndertowServletWebServerFactory;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for an Undertow-based servlet web
 * server.
 */
@AutoConfiguration
@ConditionalOnClass({ Servlet.class, Undertow.class, SslClientAuthMode.class })
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(UndertowServerProperties.class)
@Import({ UndertowWebServerConfiguration.class, ServletWebServerConfiguration.class })
public final class UndertowServletWebServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(value = ServletWebServerFactory.class, search = SearchStrategy.CURRENT)
    UndertowServletWebServerFactory undertowServletWebServerFactory(
            ObjectProvider<UndertowDeploymentInfoCustomizer> deploymentInfoCustomizers,
            ObjectProvider<UndertowBuilderCustomizer> builderCustomizers) {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        factory.addDeploymentInfoCustomizers(deploymentInfoCustomizers.orderedStream()
                .toArray(UndertowDeploymentInfoCustomizer[]::new));
        builderCustomizers.orderedStream().forEach(factory::addBuilderCustomizers);
        return factory;
    }

    @Bean
    UndertowServletWebServerFactoryCustomizer undertowServletWebServerFactoryCustomizer(
            UndertowServerProperties undertowProperties) {
        return new UndertowServletWebServerFactoryCustomizer(undertowProperties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(io.undertow.websockets.jsr.Bootstrap.class)
    static class UndertowWebSocketConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "websocketServletWebServerCustomizer")
        UndertowWebSocketServletWebServerCustomizer websocketServletWebServerCustomizer() {
            return new UndertowWebSocketServletWebServerCustomizer();
        }

    }

}
