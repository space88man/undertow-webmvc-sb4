package biz.example.web.undertow.servlet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.server.servlet.ServletWebServerSettings;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

import biz.example.web.undertow.UndertowDeploymentInfoCustomizer;
import biz.example.web.undertow.UndertowResourceManager;
import biz.example.web.undertow.UndertowWebServer;
import biz.example.web.undertow.UndertowWebServerFactory;

/**
 * Spring Boot 4 "Trailer" implementation for Undertow Servlet 6.1 support.
 * Manually implements the factory dropped in the SB4 core.
 */
public class UndertowServletWebServerFactory extends UndertowWebServerFactory
        implements ConfigurableServletWebServerFactory, ResourceLoaderAware {

    private List<UndertowDeploymentInfoCustomizer> deploymentInfoCustomizers = new ArrayList<>();
    private ResourceLoader resourceLoader;
    private final ServletWebServerSettings settings = new ServletWebServerSettings();
    private boolean eagerFilterInit = true;
    private boolean preservePathOnForward = false;

    public UndertowServletWebServerFactory() {
        super();
    }

    public UndertowServletWebServerFactory(int port) {
        super(port);
    }

    @Override
    public WebServer getWebServer(ServletContextInitializer... initializers) {
        DeploymentInfo deployment = createDeploymentInfo(initializers);
        ServletContainer container = Servlets.defaultContainer();
        DeploymentManager manager = container.addDeployment(deployment);
        // deploy() eagerly so the ServletContextInitializer chain fires (including
        // SpringBoot's getSelfInitializer()) before Spring's finishBeanFactoryInitialization
        // creates MVC beans that need a ServletContext (e.g. resourceHandlerMapping).
        manager.deploy();
        Undertow.Builder builder = createBuilder();
        Iterable<UndertowWebServer.HttpHandlerFactory> httpHandlerFactories = createHttpHandlerFactories(
                new DeploymentManagerHttpHandlerFactory(manager));
        return new UndertowServletWebServer(builder, httpHandlerFactories, getContextPath(), getPort() >= 0);
    }

    private DeploymentInfo createDeploymentInfo(ServletContextInitializer... initializers) {
        DeploymentInfo deployment = Servlets.deployment()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath(getContextPath())
                .setDisplayName("Spring Boot Undertow")
                .setDeploymentName("spring-boot-undertow");
        deployment.setEagerFilterInit(this.eagerFilterInit);
        deployment.setPreservePathOnForward(this.preservePathOnForward);

        deployment.addServletContainerInitializer(new ServletContainerInitializerInfo(
                ServletContextInitializerContainerInitializer.class,
                new ImmediateInstanceFactory<>(
                        new ServletContextInitializerContainerInitializer(initializers)),
                java.util.Collections.emptySet()));

        if (this.resourceLoader != null) {
            deployment.setResourceManager(new UndertowResourceManager(this.resourceLoader));
        }

        for (UndertowDeploymentInfoCustomizer customizer : this.deploymentInfoCustomizers) {
            customizer.customize(deployment);
        }

        return deployment;
    }

    public void addDeploymentInfoCustomizers(UndertowDeploymentInfoCustomizer... customizers) {
        Assert.notNull(customizers, "Customizers must not be null");
        this.deploymentInfoCustomizers.addAll(Arrays.asList(customizers));
    }

    public boolean isEagerFilterInit() {
        return this.eagerFilterInit;
    }

    public void setEagerFilterInit(boolean eagerFilterInit) {
        this.eagerFilterInit = eagerFilterInit;
    }

    public boolean isPreservePathOnForward() {
        return this.preservePathOnForward;
    }

    public void setPreservePathOnForward(boolean preservePathOnForward) {
        this.preservePathOnForward = preservePathOnForward;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public ServletWebServerSettings getSettings() {
        return this.settings;
    }
}