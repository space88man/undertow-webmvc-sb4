package biz.example.web.undertow;

import java.io.IOException;
import java.net.URL;

import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.handlers.resource.ResourceChangeListener;
import io.undertow.server.handlers.resource.URLResource;

import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * Adapter that maps Spring's {@link ResourceLoader} to Undertow's
 * {@link ResourceManager}.
 * Essential for serving static content or metadata in a Spring Boot "Trailer"
 * environment.
 */
public class UndertowResourceManager implements ResourceManager {

    private final ResourceLoader resourceLoader;

    public UndertowResourceManager(ResourceLoader resourceLoader) {
        Assert.notNull(resourceLoader, "ResourceLoader must not be null");
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Resource getResource(String path) throws IOException {
        String resourcePath = (path.startsWith("/") ? path : "/" + path);
        // Use explicit "classpath:" scheme to avoid WebApplicationContext.getResourceByPath()
        // which returns a ServletContextResource, causing servletContext.getResource() to
        // call back into this ResourceManager and loop infinitely.
        org.springframework.core.io.Resource resource = this.resourceLoader.getResource("classpath:" + resourcePath);

        if (resource.exists()) {
            URL url = resource.getURL();
            // HERE: Wrap the URLResource with our "Hider"
            return LoaderHidingResource.hide(new URLResource(url, path));
        }
        return null;
    }

    @Override
    public boolean isResourceChangeListenerSupported() {
        // Spring's standard ResourceLoader doesn't support push notifications for
        // changes
        return false;
    }

    @Override
    public void registerResourceChangeListener(ResourceChangeListener listener) {
        throw new UnsupportedOperationException("Resource change listeners are not supported");
    }

    @Override
    public void removeResourceChangeListener(ResourceChangeListener listener) {
        throw new UnsupportedOperationException("Resource change listeners are not supported");
    }

    @Override
    public void close() throws IOException {
        // No-op for the resource loader bridge
    }
}