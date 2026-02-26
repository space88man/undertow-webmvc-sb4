# Undertow Embedded Web Server — Spring Boot 4 Implementation Guide

## Goal

Implement `spring-boot-undertow`: an embedded Undertow web server module for Spring Boot 4
supporting **Servlet** (Jakarta Servlet 6.1),
using Undertow `2.4.0-BETA2` which is the first Undertow release targeting Servlet 6.1.

We specifically avoid a reactive/webflux implementation as that requires internal classes
from Spring Framework 7.x

---

## Package Structure

| Purpose | Package |
|---|---|
| **Your implementation** | `biz.example.web.undertow` |
| **Spring Boot 4 pattern reference** | `biz.example.web.tomcat` |
| **Spring Boot 3.5.x reference** (non-compiling) | `reference/spring-boot-3.5/src` |

---

## Reference Material Rules

### Undertow
Undertow 2.4.0.Beta2 has moved EE support to new namespaces
- core: io.undertow:undertow-core:2.4.0.Beta2
- servlet: io.undertow.ee:undertow-servlet:2.0.0.Beta2
- websocket: io.undertow.ee.undertow-servlet:2.0.0.Beta2

### `reference/spring-boot-4/tomcat/src` (PRIMARY pattern reference)
- This is a **verbatim copy of Spring Boot 4 Tomcat**
- Use this to understand Spring Boot 4 SPI patterns
- When in doubt about how something should be structured in SB4, look here

### `reference/spring-boot-4/jetty/src` (PRIMARY pattern reference)
- This is a **verbatim copy of Spring Boot 4 Jetty**
- Use this to understand Spring Boot 4 SPI patterns
- When in doubt about how something should be structured in SB4, look here

### `reference/spring-boot-3.5/src` (SECONDARY logic reference)
- Contains all Spring Boot 3.5.x autoconfiguration classes that reference Undertow
- **NOT expected to compile** against `spring-boot-starter-parent 4.0.3`
- Use this to understand **what Undertow-specific logic to port**
- Key classes to study:
  - `ServerProperties.java` — extract the `Undertow` inner class
  - `EmbeddedWebServerFactoryCustomizerAutoConfiguration.java` — SB3 centralized wiring
  - `UndertowWebServerFactoryCustomizer.java` — **most complex class, port carefully**
  - `UndertowServletWebServerFactoryCustomizer.java` — servlet-only properties
  - `ReactiveWebServerFactoryConfiguration.java` — `EmbeddedUndertow` inner class
  - `ServletWebServerFactoryConfiguration.java` — `EmbeddedUndertow` inner class
  - `WebSocketServletAutoConfiguration.java` — Undertow WebSocket wiring
  - `UndertowWebSocketServletWebServerCustomizer.java` — port verbatim

---

## Architecture Overview

### SB3 → SB4 Structural Change

In SB3, all server configuration was **centralized** in `spring-boot-autoconfigure`.
In SB4, each server is a **separate module** with its own autoconfiguration.

```
SB3 (centralized):                      SB4 (decentralized - your target):
EmbeddedWebServerFactory                spring-boot-undertow/
  CustomizerAutoConfiguration             autoconfigure/
    UndertowWebServerFactory                UndertowServerProperties
      CustomizerConfiguration               UndertowWebServerConfiguration
                                            UndertowWebServerFactoryCustomizer
                                            UndertowServletWebServerFactoryCustomizer
                                            servlet/
                                              UndertowServletWebServerAutoConfiguration
                                              UndertowWebSocketServletWebServerCustomizer
                                            actuate/web/server/
                                              UndertowAccessLogCustomizer
                                              UndertowReactive/ServletManagement*
                                            metrics/
                                              UndertowMetricsAutoConfiguration
```

---

## Classes to Implement

### 1. `UndertowServerProperties`
**Effort: Medium | Pattern: `TomcatServerProperties` | SB3 ref: `ServerProperties.Undertow`**

Extract from `ServerProperties.Undertow` inner class in SB3 reference into a standalone
`@ConfigurationProperties("server.undertow")` class.

Properties to include:
```
maxHttpPostSize, bufferSize, directBuffers, eagerFilterInit,
maxParameters, maxHeaders, maxCookies, decodeSlash, decodeUrl,
urlCharset, alwaysSetKeepAlive, noRequestTimeout, preservePathOnForward,
accesslog.{enabled, pattern, prefix, suffix, dir, rotate},
threads.{io, worker},
options.{server: Map<String,String>, socket: Map<String,String>}
```

⚠️ **DROP** `allowEncodedSlash` — deprecated for removal since 3.0.3, do not carry forward.

---

### 2. `UndertowWebServerConfiguration`
**Effort: Easy | Pattern: `biz.example.web.tomcat.autoconfigure.TomcatWebServerConfiguration`**

```java
@ConditionalOnNotWarDeployment
@Configuration(proxyBeanMethods = false)
public class UndertowWebServerConfiguration {

    private final UndertowServerProperties undertowProperties;

    UndertowWebServerConfiguration(UndertowServerProperties undertowProperties) { ... }

    @Bean
    UndertowWebServerFactoryCustomizer undertowWebServerFactoryCustomizer(
        Environment environment, ServerProperties serverProperties) { ... }

    @Bean
    @ConditionalOnThreading(Threading.VIRTUAL)
    UndertowVirtualThreadsWebServerFactoryCustomizer undertowVirtualThreadsCustomizer() { ... }
}
```

Note: Jetty pattern preferred over Tomcat — constructor injection of properties,
no `@EnableConfigurationProperties(WebProperties.class)` needed.

---

### 3. `UndertowWebServerFactoryCustomizer` ⭐ MOST COMPLEX
**Effort: Hard | SB3 ref: `UndertowWebServerFactoryCustomizer.java`**

Implements `WebServerFactoryCustomizer<ConfigurableUndertowWebServerFactory>`.

Key sections to port:
```java
customize(factory) {
    // 1. Shared: maxHttpRequestHeaderSize → UndertowOptions.MAX_HEADER_SIZE
    // 2. mapUndertowProperties() — threads, buffers, all UndertowOptions
    // 3. mapAccessLogProperties() — 6 access log properties
    // 4. getOrDeduceUseForwardHeaders() — CloudPlatform detection
}
```

**Critical: Port the `AbstractOptions` reflection engine verbatim.**
This powers `server.undertow.options.server.*` and `server.undertow.options.socket.*`
by reflecting over `UndertowOptions` and `org.xnio.Options` fields at runtime.

Constructor change for SB4:
```java
// SB3:
UndertowWebServerFactoryCustomizer(Environment env, ServerProperties serverProperties)

// SB4:
UndertowWebServerFactoryCustomizer(Environment env, ServerProperties serverProperties,
                                   UndertowServerProperties undertowProperties)
```

Remove `mapSlashProperties()` `@SuppressWarnings("deprecation")` — drop `allowEncodedSlash`
mapping entirely, keep only `decodeSlash`.

---

### 4. `UndertowVirtualThreadsWebServerFactoryCustomizer`
**Effort: Easy | Pattern: `TomcatVirtualThreadsWebServerFactoryCustomizer`**

⚠️ **Undertow virtual threads differs from Tomcat/Jetty** — it uses `DeploymentInfo`
not the factory directly:

```java
// From SB3 EmbeddedWebServerFactoryCustomizerAutoConfiguration:
@ConditionalOnClass(DeploymentInfo.class)  // servlet-only!
@Bean
@ConditionalOnThreading(Threading.VIRTUAL)
UndertowDeploymentInfoCustomizer virtualThreadsUndertowDeploymentInfoCustomizer() {
    return (deploymentInfo) ->
        deploymentInfo.setExecutor(new VirtualThreadTaskExecutor("undertow-"));
}
```

This is a **raw lambda bean** in SB3. For SB4, consider promoting to a named class
`UndertowVirtualThreadsWebServerFactoryCustomizer` for consistency with Tomcat/Jetty,
but the lambda approach also works.

---

### 5. `UndertowServletWebServerFactoryCustomizer`
**Effort: Trivial 🍒 | SB3 ref: `UndertowServletWebServerFactoryCustomizer.java`**

Servlet-only properties — port nearly verbatim:
```java
customize(UndertowServletWebServerFactory factory) {
    factory.setEagerFilterInit(undertowProperties.isEagerFilterInit());
    factory.setPreservePathOnForward(undertowProperties.isPreservePathOnForward());
}
```
Change constructor to take `UndertowServerProperties` instead of `ServerProperties`.

---

### 6. `UndertowServletWebServerAutoConfiguration`
**Effort: Medium | Pattern: `biz.example.web.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration`**

```java
@AutoConfiguration
@ConditionalOnClass({ Servlet.class, Undertow.class, SslClientAuthMode.class })
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(UndertowServerProperties.class)
@Import({ UndertowWebServerConfiguration.class, ServletWebServerConfiguration.class })
public final class UndertowServletWebServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(value = ServletWebServerFactory.class,
                              search = SearchStrategy.CURRENT)
    UndertowServletWebServerFactory undertowServletWebServerFactory(
        ObjectProvider<UndertowDeploymentInfoCustomizer> deploymentInfoCustomizers,
        ObjectProvider<UndertowBuilderCustomizer> builderCustomizers) { ... }

    @Bean
    UndertowServletWebServerFactoryCustomizer undertowServletWebServerFactoryCustomizer(
        UndertowServerProperties undertowProperties) { ... }

    // WebSocket inner configuration — see section 8
}
```

Note: Servlet factory takes **TWO** customizer types (unlike reactive which takes one):
- `ObjectProvider<UndertowDeploymentInfoCustomizer>` — servlet deployment customization
- `ObjectProvider<UndertowBuilderCustomizer>` — builder-level customization

---

### 7. `UndertowReactiveWebServerAutoConfiguration`
**Effort: Easy | Pattern: `biz.example.web.tomcat.autoconfigure.reactive.TomcatReactiveWebServerAutoConfiguration`**

```java
@AutoConfiguration
@ConditionalOnClass({ Undertow.class, ReactiveHttpInputMessage.class })
@ConditionalOnWebApplication(type = Type.REACTIVE)
@EnableConfigurationProperties(UndertowServerProperties.class)
@Import({ UndertowWebServerConfiguration.class, ReactiveWebServerConfiguration.class })
public final class UndertowReactiveWebServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveWebServerFactory.class)
    UndertowReactiveWebServerFactory undertowReactiveWebServerFactory(
        ObjectProvider<UndertowBuilderCustomizer> builderCustomizers) { ... }
}
```

Note: Reactive factory takes only **ONE** customizer type — `UndertowBuilderCustomizer`.
**NO** `UndertowDeploymentInfoCustomizer` (that's servlet-only).
**NO** reactive WebSocket inner class (Undertow has no reactive WebSocket support).

---

### 8. `UndertowWebSocketServletWebServerCustomizer`
**Effort: Trivial 🍒 | SB3 ref: `UndertowWebSocketServletWebServerCustomizer.java`**

Port **verbatim** — zero logic changes:
```java
public class UndertowWebSocketServletWebServerCustomizer
    implements WebServerFactoryCustomizer<UndertowServletWebServerFactory>, Ordered {

    customize(factory) {
        factory.addDeploymentInfoCustomizers(deploymentInfo -> {
            WebSocketDeploymentInfo info = new WebSocketDeploymentInfo();
            deploymentInfo.addServletContextAttribute(
                WebSocketDeploymentInfo.ATTRIBUTE_NAME, info);
        });
    }
}
```

Trigger class for `@ConditionalOnClass`: `io.undertow.websockets.jsr.Bootstrap`

Register as inner `@Configuration` inside `UndertowServletWebServerAutoConfiguration`:
```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(io.undertow.websockets.jsr.Bootstrap.class)
static class UndertowWebSocketConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "websocketServletWebServerCustomizer")
    UndertowWebSocketServletWebServerCustomizer websocketServletWebServerCustomizer() { ... }
}
```

---

### 9. `UndertowAccessLogCustomizer`
**Effort: Medium | Pattern: `JettyAccessLogCustomizer` (NOT Tomcat)**

Follow Jetty's pattern — **no generics**, single class handles both reactive and servlet:
```java
// DO NOT do Tomcat style:
// UndertowAccessLogCustomizer<UndertowReactiveWebServerFactory>
// UndertowAccessLogCustomizer<UndertowServletWebServerFactory>

// DO follow Jetty style:
public class UndertowAccessLogCustomizer extends AccessLogCustomizer<UndertowWebServerFactory> {
    // single class, no type parameter at usage site
}
```

---

### 10-13. Management/Actuator Beans (×4)
**Effort: Trivial 🍒 | Pattern: Jetty management classes**

Four classes, all boilerplate substitution:

```java
// UndertowReactiveManagementChildContextConfiguration
@ConditionalOnClass(Undertow.class)
@ConditionalOnWebApplication(type = Type.REACTIVE)
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
class UndertowReactiveManagementChildContextConfiguration {
    @Bean UndertowAccessLogCustomizer undertowManagementAccessLogCustomizer(...) { ... }
}

// UndertowReactiveManagementContextAutoConfiguration
@AutoConfiguration
@ConditionalOnClass({ Undertow.class, ManagementContextFactory.class })
@ConditionalOnWebApplication(type = Type.REACTIVE)
@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
public final class UndertowReactiveManagementContextAutoConfiguration {
    @Bean static ManagementContextFactory reactiveWebChildContextFactory() {
        return new ManagementContextFactory(WebApplicationType.REACTIVE,
            ReactiveWebServerFactory.class,
            UndertowReactiveWebServerAutoConfiguration.class);  // 1 class, follow Jetty!
    }
}

// + servlet equivalents (same pattern, different types)
```

---

### 14. `UndertowMetricsAutoConfiguration`
**Effort: Research needed ⚠️**

Before implementing, verify what Micrometer provides:
```
io.micrometer:micrometer-core
  → check for io.micrometer.core.instrument.binder.undertow.*
```

If Micrometer already ships `UndertowMetrics`/binders → wrap them following
`JettyMetricsAutoConfiguration` pattern (3 beans with double `@ConditionalOnMissingBean` guard).

If NOT present → either:
  a) Contribute to Micrometer first (significant scope increase), or
  b) Defer metrics to a follow-up PR and note as known gap

---

## Key Architectural Decisions

### Customizer Types Summary
```
Undertow reactive:  ObjectProvider<UndertowBuilderCustomizer>          (1 type)
Undertow servlet:   ObjectProvider<UndertowBuilderCustomizer>          (2 types)
                    ObjectProvider<UndertowDeploymentInfoCustomizer>
```

### No Reactive WebSocket
Undertow WebSocket (`io.undertow.websockets.jsr`) is built on Jakarta EE WebSocket API
(JSR-356) which requires the Servlet layer. Reactive mode bypasses Servlet entirely.
**Do not implement reactive WebSocket support — it does not exist in Undertow.**

### `AccessLogCustomizer` — follow Jetty not Tomcat
Tomcat uses generics (`TomcatAccessLogCustomizer<TomcatReactiveWebServerFactory>`).
Jetty and Undertow should use a single non-generic class targeting the common base.

### `options.server` and `options.socket` — do not simplify
The `AbstractOptions` reflection engine in `UndertowWebServerFactoryCustomizer` must be
ported carefully. It uses `ReflectionUtils` to scan `UndertowOptions` and `org.xnio.Options`
for all public static `Option<?>` fields, enabling arbitrary option configuration via
`application.properties`. This is a key differentiator for Undertow vs other servers.

---

## Suggested Implementation Order

```
1. UndertowServerProperties               — foundation, everything depends on this
2. UndertowWebServerFactoryCustomizer     — hardest class, validate early
3. UndertowServletWebServerFactoryCustomizer — trivial, confirms properties work
4. UndertowWebServerConfiguration         — wires customizers as beans
5. UndertowServletWebServerAutoConfiguration — servlet stack end-to-end
6. UndertowReactiveWebServerAutoConfiguration — reactive stack end-to-end
7. UndertowWebSocketServletWebServerCustomizer — verbatim port
8. UndertowAccessLogCustomizer            — follow Jetty pattern
9. Management beans (×4)                 — trivial, do together
10. UndertowMetricsAutoConfiguration     — last, pending Micrometer research
```

---

## Jetty SB4 Source — Key Learnings

This section captures specific facts observed directly from the Jetty Spring Boot 4 source
files. Since Jetty is the **closest architectural match** to Undertow (both use a single
customizer type, both avoid Tomcat's complexity), these observations are the highest-value
reference for your implementation.

---

### `JettyWebServerConfiguration` — the cleanest `WebServerConfiguration` pattern

Jetty uses **constructor injection** of its server properties into the configuration class,
then reuses the injected field across multiple `@Bean` methods. Follow this over Tomcat:

```java
// PREFER this (Jetty pattern):
public class UndertowWebServerConfiguration {
    private final UndertowServerProperties undertowProperties;

    UndertowWebServerConfiguration(UndertowServerProperties undertowProperties) {
        this.undertowProperties = undertowProperties;
    }

    @Bean
    UndertowWebServerFactoryCustomizer undertowWebServerFactoryCustomizer(
            Environment environment, ServerProperties serverProperties) {
        return new UndertowWebServerFactoryCustomizer(
                environment, serverProperties, this.undertowProperties);
    }

    @Bean
    @ConditionalOnThreading(Threading.VIRTUAL)
    UndertowVirtualThreadsWebServerFactoryCustomizer undertowVirtualThreadsCustomizer() {
        return new UndertowVirtualThreadsWebServerFactoryCustomizer(this.undertowProperties);
    }
}

// AVOID this (Tomcat pattern — has extra WebProperties dependency Undertow doesn't need):
// @EnableConfigurationProperties(WebProperties.class)
// TomcatWebServerFactoryCustomizer(environment, serverProperties, tomcatProperties, webProperties)
```

Jetty also has **no `WebSocket` inner `@Configuration`** inside `JettyWebServerConfiguration`
itself — WebSocket is handled inside the servlet/reactive AutoConfiguration classes instead.
Follow this layout for Undertow.

---

### `JettyAccessLogCustomizer` — no generics, used for BOTH stacks

Jetty's access log customizer has **no generic type parameter** and the same instance type
is registered in both the reactive and servlet management child context configurations:

```java
// Both child context configs register the exact same type:
// JettyReactiveManagementChildContextConfiguration:
@Bean JettyAccessLogCustomizer jettyManagementAccessLogCustomizer(
        JettyManagementServerProperties properties) {
    return new JettyAccessLogCustomizer(properties);  // no <T>
}

// JettyServletManagementChildContextConfiguration:
@Bean JettyAccessLogCustomizer jettyManagementAccessLogCustomizer(
        JettyManagementServerProperties properties) {
    return new JettyAccessLogCustomizer(properties);  // exact same type
}
```

Contrast with Tomcat which parameterises by factory type:
```java
new TomcatAccessLogCustomizer<TomcatReactiveWebServerFactory>(...)
new TomcatAccessLogCustomizer<TomcatServletWebServerFactory>(...)
```

**For Undertow: follow Jetty — one `UndertowAccessLogCustomizer` class, no generics.**

---

### `ManagementContextFactory` — Jetty uses 1 config class, Tomcat uses 2

Jetty's `ManagementContextFactory` bean passes only **one** configuration class:
```java
// Jetty (1 class):
new ManagementContextFactory(WebApplicationType.REACTIVE, ReactiveWebServerFactory.class,
        JettyReactiveWebServerAutoConfiguration.class);

// Tomcat (2 classes — needs extra TomcatWebServerConfiguration):
new ManagementContextFactory(WebApplicationType.REACTIVE, ReactiveWebServerFactory.class,
        TomcatWebServerConfiguration.class,
        TomcatReactiveWebServerAutoConfiguration.class);
```

Tomcat needs the extra class because its customizer wiring is in a separate
`TomcatWebServerConfiguration`. Since Undertow's pattern mirrors Jetty (customizers also
in a shared `UndertowWebServerConfiguration` imported by the AutoConfiguration),
**use the 1-class Jetty pattern**:
```java
new ManagementContextFactory(WebApplicationType.REACTIVE, ReactiveWebServerFactory.class,
        UndertowReactiveWebServerAutoConfiguration.class);
```

---

### `JettyServletWebServerAutoConfiguration` — single customizer type

Jetty's servlet factory bean takes only `JettyServerCustomizer` — one type:
```java
JettyServletWebServerFactory jettyServletWebServerFactory(
        ObjectProvider<JettyServerCustomizer> serverCustomizers)
```

Undertow's servlet factory needs **two** types (confirmed from SB3 source):
```java
UndertowServletWebServerFactory undertowServletWebServerFactory(
        ObjectProvider<UndertowDeploymentInfoCustomizer> deploymentInfoCustomizers,
        ObjectProvider<UndertowBuilderCustomizer> builderCustomizers)
```

This is the **key structural difference** between Jetty and Undertow servlet factories.
Do not accidentally simplify Undertow to one type following the Jetty pattern.

---

### `JettyReactiveWebServerAutoConfiguration` — `@ConditionalOnClass` sentinel classes

Jetty reactive uses these classes as presence guards:
```java
@ConditionalOnClass({ Server.class, ServletHolder.class, ReactiveHttpInputMessage.class })
```

For Undertow reactive, the equivalent is:
```java
@ConditionalOnClass({ Undertow.class, ReactiveHttpInputMessage.class })
```

Note: Undertow does NOT need a `ServletHolder` equivalent — Undertow reactive wraps
the `HttpHandler` directly without a servlet holder abstraction.

---

### `JettyServletWebServerAutoConfiguration` — WebSocket inner class placement

Jetty places its WebSocket configuration as a **static inner class** inside the servlet
AutoConfiguration, not as a separate top-level `@AutoConfiguration`:

```java
public final class JettyServletWebServerAutoConfiguration {

    @Bean JettyServletWebServerFactory ...

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JakartaWebSocketServletContainerInitializer.class)
    static class JettyWebSocketConfiguration {            // ← inner class
        @Bean WebSocketJettyServletWebServerFactoryCustomizer ...
        @Bean WebServerFactoryCustomizer<JettyServletWebServerFactory> // ← upgrade filter
            websocketUpgradeFilterWebServerCustomizer() { ... }
    }
}
```

Follow this exact placement for Undertow — WebSocket goes inside
`UndertowServletWebServerAutoConfiguration` as a static inner class.

---

### Jetty WebSocket servlet vs reactive — bean count difference

Jetty servlet WebSocket needs **2 beans**, reactive needs only **1 bean**:

```
Jetty servlet WebSocket:
  WebSocketJettyServletWebServerFactoryCustomizer    ← configures WebSocket support
  websocketUpgradeFilterWebServerCustomizer          ← registers WebSocketUpgradeFilter

Jetty reactive WebSocket:
  WebSocketJettyReactiveWebServerFactoryCustomizer   ← configures WebSocket support only
```

**Undertow servlet WebSocket needs only 1 bean** (even simpler than Jetty servlet):
```
Undertow servlet WebSocket:
  UndertowWebSocketServletWebServerCustomizer        ← adds WebSocketDeploymentInfo attribute
  // NO upgrade filter bean needed — Undertow handles upgrades internally
```

This is because Undertow's WebSocket activation is a simple `DeploymentInfo` attribute,
not a filter chain manipulation like Jetty requires.

---

### `JettyMetricsAutoConfiguration` — the `@ConditionalOnMissingBean` double-guard pattern

Every metrics bean guards against **both** the raw Micrometer class AND the Spring Boot
binder wrapper, allowing users to opt out via either:

```java
@Bean
@ConditionalOnMissingBean({ JettyServerThreadPoolMetrics.class,       // raw Micrometer
                             JettyServerThreadPoolMetricsBinder.class }) // SB wrapper
JettyServerThreadPoolMetricsBinder jettyServerThreadPoolMetricsBinder(MeterRegistry reg) {
    return new JettyServerThreadPoolMetricsBinder(reg);
}
```

Apply this exact double-guard pattern to every bean in `UndertowMetricsAutoConfiguration`.

The `@AutoConfiguration` ordering annotation used by Jetty metrics:
```java
@AutoConfiguration(
    afterName = "org.springframework.boot.micrometer.metrics.autoconfigure
                  .CompositeMeterRegistryAutoConfiguration")
```
Use the same `afterName` for `UndertowMetricsAutoConfiguration`.

---

### Jetty `JettyServerProperties` — what Undertow properties are simpler

Comparing Jetty vs Undertow access log properties shows Undertow is leaner:

```
Jetty Accesslog (9 properties):          Undertow Accesslog (6 properties):
  enabled                                  enabled
  format (NCSA/EXTENDED_NCSA enum)         pattern (String "common")
  customFormat                             prefix
  filename                                 suffix
  fileDateFormat                           dir (File)
  retentionPeriod                          rotate
  append
  ignorePaths
  [+ format enum class]
```

Undertow's access log is intentionally simpler — do not add Jetty's extra properties.

Jetty thread model vs Undertow:
```
Jetty Threads:                           Undertow Threads:
  acceptors  (Integer, default -1)         io     (Integer, CPU-derived)
  selectors  (Integer, default -1)         worker (Integer, 8 * io)
  max        (Integer, default 200)
  min        (Integer, default 8)
  maxQueueCapacity (Integer)
  idleTimeout (Duration, 60s)
```

Undertow's threading model is fundamentally different — IO threads + worker threads
(XNIO model) vs Jetty's NIO acceptor/selector/worker pool. Do not add Jetty thread
properties to `UndertowServerProperties`.

---

| Pitfall | Guidance |
|---|---|
| Using `ServerProperties.getUndertow()` | Use `UndertowServerProperties` directly in SB4 |
| Copying Tomcat's generic `AccessLogCustomizer<T>` | Follow Jetty's non-generic pattern |
| Adding reactive WebSocket | Undertow has none — do not add |
| Forgetting `DeploymentInfoCustomizer` in servlet factory | Servlet needs 2 customizer types, reactive needs 1 |
| Simplifying `AbstractOptions` reflection engine | Port verbatim — it's the Undertow escape hatch |
| Carrying forward `allowEncodedSlash` | Deprecated for removal in 3.0.3 — drop it |
| Adding `WebProperties` dependency | Only Tomcat needs it — Undertow/Jetty don't |
| Virtual threads as factory customizer | Undertow uses `DeploymentInfo.setExecutor()`, not factory-level |

---

## Build Configuration

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.3</version>
</parent>

<!-- Core Undertow dependency -->
<dependency>
    <groupId>io.undertow</groupId>
    <artifactId>undertow-servlet</artifactId>
    <version>2.4.0.Beta2</version>
</dependency>
<!-- undertow-core and xnio-api are transitive -->
<!-- SslClientAuthMode (xnio-api) used as @ConditionalOnClass trigger -->
```

---

## Validation Checklist

Before opening the GitHub PR:

- [ ] `UndertowServletWebServerFactory` starts successfully on a random port
- [ ] `UndertowReactiveWebServerFactory` starts successfully on a random port
- [ ] `server.undertow.threads.io` and `threads.worker` properties apply correctly
- [ ] `server.undertow.options.server.ENABLE_HTTP2=true` works via reflection engine
- [ ] `server.undertow.options.socket.TCP_NODELAY=true` works via reflection engine
- [ ] Access log writes to configured directory when `server.undertow.accesslog.enabled=true`
- [ ] WebSocket upgrade works in servlet mode
- [ ] Virtual threads apply when `spring.threads.virtual.enabled=true`
- [ ] Management port works on different port (actuate beans)
- [ ] All beans have `@ConditionalOnMissingBean` guards for user override
- [ ] Package `biz.example.web.undertow` compiles cleanly against `spring-boot-starter-parent 4.0.3`
