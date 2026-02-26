package biz.example.web.undertow;

import java.io.File;
import org.jspecify.annotations.Nullable; // SB4 Standard

import org.springframework.boot.web.server.ConfigurableWebServerFactory;

public interface ConfigurableUndertowWebServerFactory extends ConfigurableWebServerFactory {

    void addBuilderCustomizers(UndertowBuilderCustomizer... customizers);

    // Boxed types are used where Undertow has complex "deduced" defaults
    void setBufferSize(@Nullable Integer bufferSize);

    void setIoThreads(@Nullable Integer ioThreads);

    void setWorkerThreads(@Nullable Integer workerThreads);

    void setUseDirectBuffers(@Nullable Boolean useDirectBuffers);

    // Primitives for the Access Log (usually defaulted in Properties)
    void setAccessLogEnabled(boolean accessLogEnabled);

    void setAccessLogDirectory(@Nullable File accessLogDirectory);

    void setAccessLogPattern(@Nullable String accessLogPattern);

    void setAccessLogPrefix(@Nullable String accessLogPrefix);

    void setAccessLogSuffix(@Nullable String accessLogSuffix);

    void setAccessLogRotate(boolean accessLogRotate);

    void setUseForwardHeaders(boolean useForwardHeaders);
}