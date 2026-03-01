package biz.example.web.undertow.autoconfigure.servlet;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;

import biz.example.web.undertow.UndertowDeploymentInfoCustomizer;
import biz.example.web.undertow.autoconfigure.UndertowServerProperties;
import biz.example.web.undertow.servlet.UndertowServletWebServerFactory;

/**
 * {@link WebServerFactoryCustomizer} that configures Undertow WebSocket support by
 * registering a {@link WebSocketDeploymentInfo} as a servlet context attribute.
 * <p>
 * The buffer pool is derived from {@link UndertowServerProperties#getBufferSize()} and
 * {@link UndertowServerProperties#getDirectBuffers()} so that WebSocket honours the same
 * buffer configuration as the HTTP listener, eliminating the UT026010 warning.
 * <p>
 * Only activated when {@code io.undertow.websockets.jsr.Bootstrap} is on the classpath
 * (i.e. {@code undertow-websockets-jsr} is present), via the
 * {@code @ConditionalOnClass} guard on the enclosing configuration class in
 * {@link UndertowServletWebServerAutoConfiguration}.
 */
public class UndertowWebSocketServletWebServerCustomizer
        implements WebServerFactoryCustomizer<UndertowServletWebServerFactory>, Ordered {

    private final UndertowServerProperties undertowProperties;

    public UndertowWebSocketServletWebServerCustomizer(UndertowServerProperties undertowProperties) {
        this.undertowProperties = undertowProperties;
    }

    @Override
    public void customize(UndertowServletWebServerFactory factory) {
        factory.addDeploymentInfoCustomizers(new WebsocketDeploymentInfoCustomizer(createByteBufferPool()));
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private ByteBufferPool createByteBufferPool() {
        int bufferSize = getBufferSize();
        boolean directBuffers = isDirectBuffers();
        // threadLocalCacheSize=12, maxPoolSize=100 — same defaults Undertow uses internally
        return new DefaultByteBufferPool(directBuffers, bufferSize, 100, 12);
    }

    private int getBufferSize() {
        if (this.undertowProperties.getBufferSize() != null) {
            return (int) this.undertowProperties.getBufferSize().toBytes();
        }
        // Mirror Undertow's own default derivation from max heap
        long maxMemory = Runtime.getRuntime().maxMemory();
        return (maxMemory <= 128 * 1024 * 1024L) ? 512 : 16 * 1024;
    }

    private boolean isDirectBuffers() {
        if (this.undertowProperties.getDirectBuffers() != null) {
            return this.undertowProperties.getDirectBuffers();
        }
        // Mirror Undertow's own default — direct if > 128MB heap
        return Runtime.getRuntime().maxMemory() > 128 * 1024 * 1024L;
    }

    private static final class WebsocketDeploymentInfoCustomizer implements UndertowDeploymentInfoCustomizer {

        private final ByteBufferPool bufferPool;

        private WebsocketDeploymentInfoCustomizer(ByteBufferPool bufferPool) {
            this.bufferPool = bufferPool;
        }

        @Override
        public void customize(DeploymentInfo deploymentInfo) {
            WebSocketDeploymentInfo info = new WebSocketDeploymentInfo();
            info.setBuffers(this.bufferPool);
            deploymentInfo.addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, info);
        }

    }

}
