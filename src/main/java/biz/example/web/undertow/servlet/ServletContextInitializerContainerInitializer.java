package biz.example.web.undertow.servlet;

import java.util.Set;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.springframework.boot.web.servlet.ServletContextInitializer;

/**
 * Undertow {@link ServletContainerInitializer} that delegates to 
 * Spring Boot {@link ServletContextInitializer}s.
 */
class ServletContextInitializerContainerInitializer implements ServletContainerInitializer {

    private final ServletContextInitializer[] initializers;

    ServletContextInitializerContainerInitializer(ServletContextInitializer[] initializers) {
        this.initializers = initializers;
    }

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {
        for (ServletContextInitializer initializer : this.initializers) {
            // This is where Spring Boot's Servlets (and your gRPC Servlet) 
            // actually get registered into Undertow.
            initializer.onStartup(servletContext);
        }
    }
}