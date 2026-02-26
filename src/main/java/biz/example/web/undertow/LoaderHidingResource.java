package biz.example.web.undertow;

import io.undertow.server.handlers.resource.Resource;

/**
 * Ensures that internal Spring Boot JAR structures are not accessible 
 * via the Undertow ResourceHandler.
 */
public final class LoaderHidingResource {

    private LoaderHidingResource() {
    }

    public static Resource hide(Resource resource) {
        if (resource == null) {
            return null;
        }
        String path = resource.getPath();
        if (path != null && (path.contains("BOOT-INF") || path.contains("META-INF"))) {
            return null;
        }
        return resource;
    }
}