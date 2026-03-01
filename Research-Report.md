# Research Report: Undertow 2.4 as a Spring Boot 4 Embedded Web Server

**Date:** March 1, 2026
**Module:** `biz.example.web.undertow`
**Spring Boot:** 4.0.3
**Undertow:** core `2.4.0.Beta2` / servlet `2.0.0.Beta2` / websockets `2.0.0.Beta2`
**Target servlet spec:** Jakarta Servlet 6.1

---

## 1. Objective

Implement `spring-boot-undertow`: a self-contained embedded Undertow web server module
for Spring Boot 4, supporting the full Servlet stack (Jakarta Servlet 6.1). Reactive/WebFlux
was explicitly excluded because it requires internal classes from Spring Framework 7.x that
are not part of the public SPI.

Undertow 2.4 is the first Undertow release targeting Jakarta EE 11 / Servlet 6.1.
The EE-facing artifacts were reorganised under new Maven coordinates in this release:

| Artifact | SB3 / Undertow 2.3 | SB4 / Undertow 2.4 |
|---|---|---|
| Core | `io.undertow:undertow-core:2.3.x` | `io.undertow:undertow-core:2.4.0.Beta2` |
| Servlet | `io.undertow:undertow-servlet:2.3.x` | `io.undertow.ee:undertow-servlet:2.0.0.Beta2` |
| WebSocket | `io.undertow:undertow-websockets-jsr:2.3.x` | `io.undertow.ee:undertow-websockets-jsr:2.0.0.Beta2` |

---

## 2. Architectural Shift: SB3 → SB4

Spring Boot 3 centralised all embedded server configuration in `spring-boot-autoconfigure`.
Spring Boot 4 decentralises it — each server ships its own autoconfiguration module.

```
SB3 (centralised):                           SB4 (decentralised — this module):
spring-boot-autoconfigure                    spring-boot-undertow/
  EmbeddedWebServerFactoryCustomizer           autoconfigure/
    AutoConfiguration                            UndertowServerProperties
      UndertowWebServerFactory                   UndertowWebServerConfiguration
        CustomizerConfiguration                  UndertowWebServerFactoryCustomizer
      UndertowServletWebServerFactory            UndertowServletWebServerFactoryCustomizer
        CustomizerConfiguration                  UndertowVirtualThreadsWebServerFactoryCustomizer
      UndertowServletWebServerFactory            servlet/
        Customizer                                 UndertowServletWebServerAutoConfiguration
                                                   UndertowWebSocketServletWebServerCustomizer
```

The key design reference for the SB4 module structure was the Spring Boot 4 Jetty module,
which is the closest architectural match to Undertow (single shared customizer type at the
reactive level, no `WebProperties` dependency, non-generic `AccessLogCustomizer`).

---

## 3. What Was Built

### 3.1 Core SPI Layer

| Class | Responsibility |
|---|---|
| `UndertowWebServer` | XNIO listener lifecycle: start / stop / graceful drain / port binding |
| `UndertowServletWebServer` | Wraps `DeploymentManager`; idempotent stop via `isStarted()` guard |
| `UndertowWebServerFactory` | Builder: buffers, threads, SSL, compression, access log |
| `UndertowServletWebServerFactory` | Extends factory with servlet deployment: `DeploymentInfo`, eager `deploy()` |
| `SslBuilderCustomizer` | JSSE keystore / truststore → Undertow XNIO `SSLContext` bridge |
| `AccessLogHttpHandlerFactory` | Internal — wires `AccessLogReceiver` into handler chain |
| `CompressionHttpHandlerFactory` | Internal — wraps handler in `EncodingHandler` |
| `LoaderHidingResource` | Static utility filter — blocks `BOOT-INF/` and `META-INF/` HTTP access |
| `UndertowResourceManager` | Spring `ResourceLoader` → Undertow `ResourceManager` bridge |
| `UndertowEmbeddedErrorHandler` | Routes 4xx/5xx through `ErrorController` |
| `GracefulShutdown` | XNIO drain handler; tracks active requests for coordinated shutdown |

### 3.2 Autoconfiguration Layer

| Class | Responsibility |
|---|---|
| `UndertowServerProperties` | `@ConfigurationProperties("server.undertow")` — threads, buffers, accesslog, options maps |
| `UndertowWebServerConfiguration` | `@Configuration` — wires `UndertowWebServerFactoryCustomizer` and virtual-threads customizer |
| `UndertowWebServerFactoryCustomizer` | Applies `UndertowOptions` / `org.xnio.Options` via reflection engine; forward-headers; access log |
| `UndertowServletWebServerFactoryCustomizer` | Servlet-only: `eagerFilterInit`, `preservePathOnForward` |
| `UndertowVirtualThreadsWebServerFactoryCustomizer` | `DeploymentInfo.setExecutor(new VirtualThreadTaskExecutor("undertow-"))` |
| `UndertowServletWebServerAutoConfiguration` | `@AutoConfiguration` — full servlet stack wiring; inner `UndertowWebSocketConfiguration` |
| `UndertowWebSocketServletWebServerCustomizer` | Adds `WebSocketDeploymentInfo` attribute to `DeploymentInfo` |

### 3.3 Extension SPI

| Type | Description |
|---|---|
| `ConfigurableUndertowWebServerFactory` | Public SPI interface for customizer callbacks |
| `UndertowBuilderCustomizer` | `@FunctionalInterface` — low-level `Undertow.Builder` access |
| `UndertowDeploymentInfoCustomizer` | `@FunctionalInterface` — servlet `DeploymentInfo` access (servlet-only) |

### 3.4 Service Registration

| File | Entry |
|---|---|
| `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | `UndertowServletWebServerAutoConfiguration` |

---

## 4. Key Design Decisions

### 4.1 Eager `deploy()` in `getWebServer()`

**Problem:** Spring Boot calls `getWebServer()` early in context refresh, then calls
`finishBeanFactoryInitialization()` which creates Spring MVC beans (e.g. `resourceHandlerMapping`)
that require a `ServletContext`. If `deploy()` is deferred until the first HTTP request,
the `SpringServletContainerInitializer` (which calls `setServletContext()`) fires too late.

**Fix:** `deploy()` is called eagerly inside `UndertowServletWebServerFactory.getWebServer()`,
before the builder or `UndertowServletWebServer` is constructed. `DeploymentManagerHttpHandlerFactory.getHandler()`
only calls `start()` (not `deploy()`).

```java
// UndertowServletWebServerFactory.getWebServer():
manager.deploy();          // ← fires SpringServletContainerInitializer / setServletContext()
Undertow.Builder builder = createBuilder();
// ...
return new UndertowServletWebServer(manager, builder, ...);
```

### 4.2 `UndertowResourceManager` — `classpath:` prefix

**Problem:** When `UndertowResourceManager.getResource()` delegated to a
`WebApplicationContext` via `resourceLoader.getResource(path)`, `WebApplicationContext.getResourceByPath()`
returned a `ServletContextResource`. Resolving that resource called `servletContext.getResource()`,
which re-entered `UndertowResourceManager.getResource()` — infinite recursion → `StackOverflowError`.

**Fix:** Prefix the path with `classpath:` to force resolution through the classpath loader,
bypassing `getResourceByPath()` entirely:

```java
org.springframework.core.io.Resource resource =
        this.resourceLoader.getResource("classpath:" + resourcePath);
```

### 4.3 `LoaderHidingResource` — static utility, not decorator

Using a decorator (`implements Resource`) that held a reference back to the `ResourceManager`
would recreate the recursive call chain described in §4.2. The final design is a pure static
filter with no state and no back-reference:

```java
public static Resource hide(Resource resource) {
    if (resource == null) return null;
    String path = resource.getPath();
    if (path != null && (path.contains("BOOT-INF") || path.contains("META-INF"))) {
        return null;
    }
    return resource;
}
```

### 4.4 `AbstractOptions` Reflection Engine

`UndertowWebServerFactoryCustomizer` ports the SB3 reflection engine verbatim. It scans
`io.undertow.UndertowOptions` and `org.xnio.Options` for all public static `Option<?>` fields
at runtime, enabling arbitrary server/socket option configuration via `application.properties`:

```properties
server.undertow.options.server.ENABLE_HTTP2=true
server.undertow.options.socket.TCP_NODELAY=true
```

This is a key Undertow differentiator — no equivalent mechanism exists in Tomcat or Jetty.

### 4.5 Virtual Threads — `DeploymentInfo`, not factory

Tomcat and Jetty apply virtual threads at the factory level. Undertow applies them at
`DeploymentInfo` level via `setExecutor()` — this is a servlet-only customisation (no
reactive equivalent):

```java
deploymentInfo.setExecutor(new VirtualThreadTaskExecutor("undertow-"));
```

### 4.6 WebSocket — servlet only, no reactive

Undertow WebSocket (`io.undertow.websockets.jsr`) is built on the Jakarta EE WebSocket API
(JSR-356 / Jakarta WebSocket 2.2), which requires the Servlet layer. There is no reactive
WebSocket support in Undertow — none was implemented.

### 4.7 RFC 8441 (WebSocket over HTTP/2) — not supported

Undertow 2.4 does not advertise `SETTINGS_ENABLE_CONNECT_PROTOCOL` in its HTTP/2 SETTINGS
frame. The RFC 8441 extended CONNECT method is therefore unavailable server-side. This is
an Undertow 2.4 limitation, not an implementation gap.

---

## 5. Production Bugs Found and Fixed by Tests

Two bugs originated from imprecise SB3 → SB4 migration; a third was an Undertow-specific
teardown race discovered during WebSocket integration testing.

### Bug 1: `PortInUseException` Silently Swallowed

**Root cause:** SB4 changed the `PortInUseException` helper from a factory method (SB3)
to a throwing consumer (SB4):

```java
// SB3 — returned the exception, caller was responsible for throwing:
PortInUseException.ifPortBindingException(ex,
        (bindEx) -> new PortInUseException(this.port, bindEx));

// SB4 — throws internally:
PortInUseException.throwIfPortBindingException(ex, () -> this.port);
```

The SB3 call shape compiled silently against the SB4 jar. The return value was discarded,
so no `PortInUseException` was ever thrown. The symptom was that SB4's human-readable
`"Port 8080 already in use"` startup failure message never appeared — the process silently
swallowed the bind error.

**Fix:** Use `throwIfPortBindingException`.
**Regression test:** `portInUseExceptionWhenPortAlreadyBound` in `UndertowServletWebServerFactoryTests`.

### Bug 2: Double `stop()` NPE

**Root cause:** The SB4 `WebServer` contract specifies:

> Calling `stop()` on an already-stopped server **has no effect**.

`UndertowServletWebServer.stop()` called `super.stop()` (which guards itself) but then
unconditionally called `this.manager.stop()`. Undertow's `DeploymentManagerImpl.stop()` is
not idempotent — calling it a second time throws `NullPointerException`. The `isStarted()`
guard on `UndertowWebServer` was `package-private` and therefore not accessible from the
`servlet/` subpackage.

**Fix:** Promote `isStarted()` to `protected`; guard `stop()` in `UndertowServletWebServer`
with `if (!isStarted()) return;` before delegating.  
**Regression test:** `stopOnAlreadyStoppedServerDoesNotThrow` in `UndertowServletWebServerFactoryTests`.

### Bug 3: WebSocket `@OnClose` / XNIO Worker Teardown Race (`XNIO007007`)

**Root cause:** The original `stop()` order was:

```
super.stop()          ← undertow.stop() terminates the XNIO worker pool immediately
manager.undeploy()    ← JSR-356 session teardown needs to dispatch onto a worker thread
manager.stop()
```

`DeploymentManager.undeploy()` triggers `ServerWebSocketContainer` teardown, which calls
`UndertowSession.close0()`, which dispatches the JSR-356 `@OnClose` callback via
`WorkerThread.execute()`. By the time `undeploy()` runs in this order the XNIO worker pool
is already terminated, so every `@OnClose` dispatch throws:

```
java.util.concurrent.RejectedExecutionException: XNIO007007: Thread is terminating
    at org.xnio.nio.WorkerThread.execute(WorkerThread.java:632)
    at io.undertow.websockets.jsr.UndertowSession.close0(UndertowSession.java:362)
```

A secondary race exists on the graceful shutdown path: `GracefulShutdownHttpHandler.awaitShutdown()`
reports idle as soon as the last HTTP exchange completes, but a WebSocket `@OnClose` callback
may still be in flight on an XNIO worker thread at that instant. If the caller proceeds
directly to `server.stop()`, the same `RejectedExecutionException` occurs.

**Fix — two independent parts, both required:**

**Part 1 — `UndertowServletWebServer.stop()`: reverse teardown order + quiesce**

```
manager.undeploy()    ← JSR-356 closes sessions — XNIO worker still alive ✓
manager.stop()
Thread.sleep(100ms)   ← quiesce: client CLOSE frames may still arrive on I/O thread;
                         give in-flight @OnClose dispatches time to complete
super.stop()          ← XNIO worker pool terminated safely
```

The 100 ms quiesce covers a second race window: after `undeploy()`, the XNIO I/O threads
are still alive and may receive a WebSocket CLOSE frame from a connected client (sent by
the test client on teardown). That frame triggers `FrameHandler.onFullCloseMessage` →
`UndertowSession.close0()` → `WorkerThread.execute()`. Without the quiesce, `super.stop()`
can race ahead of this path even though `undeploy()` has already run.

**Part 2 — `GracefulShutdown.awaitShutdown()`: quiesce before signalling idle**

```
handler.awaitShutdown()   ← idle when last HTTP exchange completes
Thread.sleep(200ms)       ← allow @OnClose dispatches to drain
callback(IDLE)            ← caller proceeds to server.stop() safely
```

There is no API in Undertow or JSR-356 to wait for all `@OnClose` callbacks to complete —
`getOpenSessions()` is scoped per endpoint, and `ServerWebSocketContainer` exposes no
"all sessions closed" signal. The sleep is the only available mechanism short of patching
Undertow itself.

**Why both parts are needed:**

| Call path | Covered by |
|---|---|
| `server.stop()` called directly (test teardown, context close) | Part 1 only |
| Graceful shutdown: `shutDownGracefully()` → `callback(IDLE)` → `server.stop()` | Part 1 + Part 2 |

Part 1 alone fixes both paths at the machine level. Part 2 adds a defensive margin on the
graceful path to handle any `@OnClose` work that the XNIO I/O thread queues after
`awaitShutdown()` returns but before `stop()` is entered.

---

## 6. SB4 API Migration Notes

Differences discovered during implementation that are not obvious from SB3 source:

| Topic | SB3 | SB4 |
|---|---|---|
| `TestRestTemplate` | `org.springframework.boot.test.web.client.TestRestTemplate` | **Removed** — use JDK `HttpClient` or `WebClient` |
| `LocalServerPort` | `org.springframework.boot.web.server.LocalServerPort` | `org.springframework.boot.test.web.server.LocalServerPort` |
| `WebServerApplicationContext` | `org.springframework.boot.web.context.WebServerApplicationContext` | `org.springframework.boot.web.server.context.WebServerApplicationContext` |
| `PortInUseException` helper | `ifPortBindingException(ex, factory)` | `throwIfPortBindingException(ex, portSupplier)` |
| `DeploymentManager.deploy()` | declared `throws ServletException` | no checked exception |
| `AccessLogCustomizer` | generic `<T extends WebServerFactory>` (Tomcat) | non-generic, targets common base (Jetty/Undertow pattern) |
| `ManagementContextFactory` | varies | Jetty passes 1 config class; Tomcat passes 2; Undertow follows Jetty |

### `TypeExcludeFilter` in `@SpringBootTest`

Spring Boot's `TypeExcludeFilter` prevents `@SpringBootApplication` component scanning from
picking up inner classes declared inside the `@SpringBootTest` class itself (to avoid
accidentally bootstrapping test infrastructure). Controllers defined as inner classes of the
IT class must be explicitly registered:

```java
@SpringBootApplication
@Import(HelloController.class)   // ← required; component scan alone is not sufficient
static class TestApplication {
}
```

---

## 7. Test Coverage

### 7.1 Surefire (unit + `ApplicationContextRunner`)

| Test class | Count | What it covers |
|---|---|---|
| `UndertowServerPropertiesTests` | 6 | Property binding, defaults, `@ConfigurationProperties` structure |
| `UndertowWebServerFactoryCustomizerTests` | 11 | Option application, access log, forward headers, reflection engine |
| `UndertowServletWebServerFactoryCustomizerTests` | 4 | `eagerFilterInit`, `preservePathOnForward` |
| `UndertowServletWebServerAutoConfigurationTests` | 8 | Bean conditions, WebSocket conditional, `@ConditionalOnMissingBean` guards |
| `UndertowServletWebServerFactoryTests` | 14 | Factory construction, port binding, lifecycle contract, SSL, customizers |
| `UndertowServletWebServerIntegrationTests` | 3 | HTTP GET, context path, 404 |
| `UndertowH2cIntegrationTests` | 2 | H2C prior knowledge upgrade, `getProtocol()` = HTTP/2.0 |
| `UndertowWebSocketIntegrationTests` | 2 | WS handshake, JSON echo RPC |
| `AccessLogHttpHandlerFactoryTests` | 6 | Access log handler chain wiring |
| `CompressionHttpHandlerFactoryTests` | 8 | Compression handler conditions and configuration |
| `GracefulShutdownTests` | 2 | Graceful drain: completes / aborts under load |
| **Total** | **66** | |

### 7.2 Failsafe (full `@SpringBootApplication` IT)

| Test | What it proves |
|---|---|
| `helloEndpointReturns200WithExpectedBody` | Full autoconfiguration chain → Spring MVC → `/hello` → 200 |
| `unknownPathReturns404` | Default 404 handling via `DispatcherServlet` |
| `embeddedServerIsUndertow` | `applicationContext.getWebServer()` is `UndertowServletWebServer`, not Tomcat |

**Total: 66 surefire + 3 failsafe = 69 passing, 0 failures.**

---

## 8. Known Gaps

| Gap | Reason |
|---|---|
| RFC 8441 (WebSocket over H2C) | Undertow 2.4 does not advertise `SETTINGS_ENABLE_CONNECT_PROTOCOL` — not implementable at the application layer |
| Management / actuator beans | Next phase; servlet stack must be stable prior to management port work |
| `UndertowMetricsAutoConfiguration` | Requires research: Micrometer does not ship Undertow binders as of this writing |
| SSL integration tests | PEM fixtures present at `src/test/resources/ssl/`; test implementation deferred |
| Reactive / WebFlux | Explicitly excluded — requires internal Spring Framework 7.x classes |

---

## 9. Validation Checklist

- [x] `UndertowServletWebServerFactory` starts on a random port
- [x] Jakarta Servlet 6.1 dispatch path works end-to-end
- [x] `server.undertow.threads.io` and `threads.worker` apply correctly
- [x] `server.undertow.options.server.ENABLE_HTTP2=true` via reflection engine
- [x] `server.undertow.options.socket.TCP_NODELAY=true` via reflection engine
- [x] H2C prior-knowledge upgrade (`PRI * HTTP/2.0`)
- [x] WebSocket HTTP/1.1 upgrade and message exchange
- [x] Virtual threads apply when `spring.threads.virtual.enabled=true`
- [x] `PortInUseException` thrown (not swallowed) on port conflict
- [x] `stop()` called twice does not throw
- [x] Full `@SpringBootApplication` context loads and serves HTTP traffic
- [x] `AutoConfiguration.imports` registration is correct
- [x] All beans have `@ConditionalOnMissingBean` guards for user override
- [x] Package `biz.example.web.undertow` compiles cleanly against `spring-boot-starter-parent 4.0.3`
- [ ] Access log writes to configured directory (deferred)
- [ ] SSL/TLS integration test (deferred)
- [ ] Management port on different port (deferred)
