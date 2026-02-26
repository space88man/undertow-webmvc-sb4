package biz.example.web.undertow;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xnio.channels.BoundChannel;

import org.springframework.boot.web.server.GracefulShutdownCallback;
import org.springframework.boot.web.server.GracefulShutdownResult;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link WebServer} that can be used to control an Undertow web server.
 *
 * @author Ivan Sopov
 * @author Andy Wilkinson
 * @author Eddú Meléndez
 * @author Christoph Dreis
 * @author Kristine Jetzke
 */
public class UndertowWebServer implements WebServer {

    private static final Log logger = LogFactory.getLog(UndertowWebServer.class);

    private final Object monitor = new Object();

    private final Undertow.Builder builder;

    private final Iterable<HttpHandlerFactory> httpHandlerFactories;

    private final boolean autoStart;

    private Undertow undertow;

    private volatile boolean started = false;

    private volatile GracefulShutdown gracefulShutdown;

    private volatile List<BoundChannel> channels = Collections.emptyList();

    /**
     * Create a new {@link UndertowWebServer} instance.
     * @param builder the Undertow builder
     * @param httpHandlerFactories the handler factories
     * @param autoStart whether the server should start automatically
     */
    public UndertowWebServer(Undertow.Builder builder,
            Iterable<HttpHandlerFactory> httpHandlerFactories, boolean autoStart) {
        this.builder = builder;
        this.httpHandlerFactories = httpHandlerFactories;
        this.autoStart = autoStart;
    }

    @Override
    public void start() throws WebServerException {
        synchronized (this.monitor) {
            if (this.started) {
                return;
            }
            try {
                if (!this.autoStart) {
                    return;
                }
                if (this.undertow == null) {
                    this.undertow = createUndertowServer();
                }
                this.undertow.start();
                this.started = true;
                this.channels = extractChannels();
                String message = getStartLogMessage();
                logger.info(message);
            }
            catch (Exception ex) {
                try {
                    PortInUseException.ifPortBindingException(ex,
                            (bindException) -> new PortInUseException(getPort(), ex));
                    throw new WebServerException("Unable to start embedded Undertow", ex);
                }
                finally {
                    stopSilently();
                }
            }
        }
    }

    private Undertow createUndertowServer() {
        this.gracefulShutdown = null;
        HttpHandler handler = createHttpHandler();
        this.builder.setHandler(handler);
        return this.builder.build();
    }

    protected HttpHandler createHttpHandler() {
        HttpHandler handler = null;
        for (HttpHandlerFactory factory : this.httpHandlerFactories) {
            handler = factory.getHandler(handler);
            if (handler instanceof GracefulShutdownHttpHandler gracefulHandler) {
                this.gracefulShutdown = new GracefulShutdown(gracefulHandler);
            }
        }
        return handler;
    }

    @SuppressWarnings("unchecked")
    private List<BoundChannel> extractChannels() {
        Field channelsField = ReflectionUtils.findField(Undertow.class, "channels");
        if (channelsField == null) {
            return Collections.emptyList();
        }
        ReflectionUtils.makeAccessible(channelsField);
        return new ArrayList<>((List<BoundChannel>) ReflectionUtils.getField(channelsField,
                this.undertow));
    }

    protected String getStartLogMessage() {
        List<String> addresses = new ArrayList<>();
        for (BoundChannel channel : this.channels) {
            SocketAddress socketAddress = channel.getLocalAddress();
            if (socketAddress instanceof InetSocketAddress inetAddress) {
                String address = (!StringUtils.hasLength(inetAddress.getHostString())
                        || inetAddress.getHostString().equals("0.0.0.0"))
                                ? "port " + inetAddress.getPort()
                                : inetAddress.getHostString() + ":" + inetAddress.getPort();
                addresses.add(address);
            }
        }
        return "Undertow started on " + String.join(", ", addresses);
    }

    private void stopSilently() {
        try {
            if (this.undertow != null) {
                this.undertow.stop();
                this.channels = Collections.emptyList();
            }
        }
        catch (Exception ex) {
            // Ignore
        }
    }

    @Override
    public void stop() throws WebServerException {
        synchronized (this.monitor) {
            if (!this.started) {
                return;
            }
            this.started = false;
            try {
                this.undertow.stop();
                this.channels = Collections.emptyList();
            }
            catch (Exception ex) {
                throw new WebServerException("Unable to stop embedded Undertow", ex);
            }
        }
    }

    @Override
    public void shutDownGracefully(GracefulShutdownCallback callback) {
        GracefulShutdown shutdown = this.gracefulShutdown;
        if (shutdown == null) {
            callback.shutdownComplete(GracefulShutdownResult.IMMEDIATE);
            return;
        }
        shutdown.shutDownGracefully(callback);
    }

    @Override
    public int getPort() {
        List<BoundChannel> localChannels = this.channels;
        if (localChannels.isEmpty()) {
            return -1;
        }
        SocketAddress socketAddress = localChannels.get(0).getLocalAddress();
        if (socketAddress instanceof InetSocketAddress inetAddress) {
            return inetAddress.getPort();
        }
        return -1;
    }

    /**
     * Returns whether the Undertow server is started.
     * @return {@code true} if started
     */
    boolean isStarted() {
        return this.started;
    }

    /**
     * Factory that creates an {@link HttpHandler}.
     */
    @FunctionalInterface
    public interface HttpHandlerFactory {

        /**
         * Create a handler wrapping the given {@code next} handler (which may be
         * {@code null} if this is the first/innermost factory).
         * @param next the next handler in the chain, or {@code null}
         * @return the handler
         */
        HttpHandler getHandler(HttpHandler next);

    }

    /**
     * Marker interface for {@link HttpHandler} instances that support graceful shutdown.
     */
    interface GracefulShutdownHttpHandler extends HttpHandler {

        void shutdown();

        boolean awaitShutdown(long timeout) throws InterruptedException;

    }

}