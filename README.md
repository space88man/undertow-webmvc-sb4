# biz-example-web-undertow

An experimental repository restoring Undertow support as an embedded web server in
Spring Boot 4 — **Spring Web MVC (servlet stack) only**.

> Created with the assistance of [Claude Sonnet 4.6](https://www.anthropic.com/claude).

---

## Status

Functional and tested. The servlet stack starts, serves HTTP/1.1, HTTP/2 cleartext (H2C),
and WebSocket traffic. See [Research-Report.md](Research-Report.md) for a full account of
the implementation, design decisions, and bugs found along the way.

---

## Important Notes

### Servlet 6.1 — Undertow 2.4 + new `io.undertow.ee` namespace

Jakarta Servlet 6.1 support arrives in **Undertow 2.4**, which reorganised the EE-facing
artifacts under a new Maven group ID. We use the first beta releases of these coordinates:

| Artifact | Group | Version |
|---|---|---|
| `undertow-core` | `io.undertow` | `2.4.0.Beta2` |
| `undertow-servlet` | `io.undertow.ee` | `2.0.0.Beta2` |
| `undertow-websockets` | `io.undertow.ee` | `2.0.0.Beta2` |

Note the **`io.undertow.ee`** group for the two EE artifacts — this is a breaking change
from the `io.undertow` group used in Undertow 2.3 / Spring Boot 3.

### WebFlux is explicitly not supported

Undertow reactive (WebFlux) support requires adapter classes inside Spring Framework 7.x
(`HttpHandlerAdapter`, `UndertowServerHttpRequest`, etc.) that are internal to the
framework and cannot be provided externally. This repository makes no attempt to implement
reactive support.

Only `spring-boot-starter-web` (Spring MVC) is supported.

---

## Coordinates

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.3</version>
</parent>

<dependency>
    <groupId>io.undertow.ee</groupId>
    <artifactId>undertow-servlet</artifactId>
    <version>2.0.0.Beta2</version>
</dependency>
<!-- undertow-core and xnio-api are pulled in transitively -->
```

Exclude the default Tomcat starter and add `spring-boot-starter-web`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## Configuration

All properties are under `server.undertow.*`, mirroring the Spring Boot 3 layout:

```properties
server.undertow.threads.io=4
server.undertow.threads.worker=32
server.undertow.buffer-size=16384
server.undertow.direct-buffers=true
server.undertow.accesslog.enabled=true
server.undertow.accesslog.dir=logs
server.undertow.options.server.ENABLE_HTTP2=true
server.undertow.options.socket.TCP_NODELAY=true
```

The `options.server.*` and `options.socket.*` maps accept any field name from
`io.undertow.UndertowOptions` and `org.xnio.Options` respectively, resolved at runtime
via reflection.

---

## Building and Testing

```bash
mvn verify
```

Runs 66 surefire unit/integration tests and 3 failsafe IT tests (full
`@SpringBootApplication` context on a random port).
