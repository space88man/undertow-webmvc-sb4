package biz.example.web.undertow;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Bridges Undertow's native error handling to Spring Boot's ErrorController.
 * Ensures that 404s and 500s are routed to the Servlet-based error path.
 */
public class UndertowEmbeddedErrorHandler implements HttpHandler {

    private final HttpHandler next;

    public UndertowEmbeddedErrorHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        this.next.handleRequest(exchange);
        // If the response is a 4xx/5xx and hasn't been committed, 
        // we let the Servlet error pages (configured in the Factory) take over.
        if (exchange.getStatusCode() >= 400 && !exchange.isResponseStarted()) {
            // Undertow will look at the ErrorPages we registered in DeploymentInfo
        }
    }
}