package biz.example.web.undertow.autoconfigure.servlet;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;

import biz.example.web.undertow.UndertowDeploymentInfoCustomizer;
import biz.example.web.undertow.servlet.UndertowServletWebServerFactory;

/**
 * {@link WebServerFactoryCustomizer} that configures Undertow WebSocket support by
 * registering a {@link WebSocketDeploymentInfo} as a servlet context attribute.
 * <p>
 * Only activated when {@code io.undertow.websockets.jsr.Bootstrap} is on the classpath
 * (i.e. {@code undertow-websockets-jsr} is present), via the
 * {@code @ConditionalOnClass} guard on the enclosing configuration class in
 * {@link UndertowServletWebServerAutoConfiguration}.
 */
public class UndertowWebSocketServletWebServerCustomizer
        implements WebServerFactoryCustomizer<UndertowServletWebServerFactory>, Ordered {

    @Override
    public void customize(UndertowServletWebServerFactory factory) {
        factory.addDeploymentInfoCustomizers(new WebsocketDeploymentInfoCustomizer());
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private static final class WebsocketDeploymentInfoCustomizer implements UndertowDeploymentInfoCustomizer {

        @Override
        public void customize(DeploymentInfo deploymentInfo) {
            WebSocketDeploymentInfo info = new WebSocketDeploymentInfo();
            deploymentInfo.addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, info);
        }

    }

}
