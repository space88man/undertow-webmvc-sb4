# SSL Support

This module implements HTTPS support for the Undertow embedded web server via
`SslBuilderCustomizer`, which bridges Spring Boot's `SslBundle` abstraction into
Undertow's `Undertow.Builder` API.

---

## How It Works

### `SslBundle` (Spring Boot)

Spring Boot 4 centralises SSL configuration in
[`org.springframework.boot.ssl.SslBundle`](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/ssl/SslBundle.html).
A bundle carries:

- a **key store** (server certificate + private key)
- a **trust store** (CA certificates)
- key alias, key password, protocol, and cipher-suite options

Bundles are typically created from PEM files via `PemSslStoreDetails` /
`PemSslStoreBundle`, or from JKS/PKCS12 stores via `JksSslStoreBundle`.  The
factory method `SslBundle.of(SslStoreBundle)` wires these into a ready-to-use
bundle.

### `SslBuilderCustomizer`

`SslBuilderCustomizer` implements `UndertowBuilderCustomizer` and is the single
point where the bundle is applied to the Undertow builder:

```
SslBundle
  └─ createSslContext()           ← JSSE SSLContext
       └─ Undertow.Builder
            └─ addHttpsListener(port, host, sslContext)
                 └─ configureClientAuth(builder)
                      └─ Options.SSL_CLIENT_AUTH_MODE  (XNIO option)
```

**Constructor:**
```java
SslBuilderCustomizer(int port,
                     @Nullable InetAddress address,
                     Ssl.@Nullable ClientAuth clientAuth,
                     SslBundle sslBundle)
```

| Parameter | Notes |
|---|---|
| `port` | Port to bind the HTTPS listener on; `0` for random |
| `address` | Bind address; `null` maps to `"0.0.0.0"` (all interfaces) |
| `clientAuth` | `NONE` / `WANT` / `NEED`; `null` treated as `NONE` |
| `sslBundle` | Spring Boot SSL bundle providing the `SSLContext` |

**`customize(Undertow.Builder)`** calls `builder.addHttpsListener(...)` and then
sets the XNIO socket option `SSL_CLIENT_AUTH_MODE`:

| `Ssl.ClientAuth` | XNIO `SslClientAuthMode` |
|---|---|
| `NEED` | `REQUIRED` |
| `WANT` | `REQUESTED` |
| `NONE` / `null` | _(not set — Undertow default: disabled)_ |

Any exception from `sslBundle.createSslContext()` is wrapped and rethrown as
`IllegalStateException("Failed to configure Undertow SSL")`.

### Integration with `UndertowWebServerFactory`

`UndertowWebServerFactory.createBuilder()` invokes `SslBuilderCustomizer`
automatically when SSL is enabled on the factory:

```java
Ssl ssl = getSsl();
if (Ssl.isEnabled(ssl)) {
    new SslBuilderCustomizer(port, address, ssl.getClientAuth(), getSslBundle())
            .customize(builder);
}
else {
    builder.addHttpListener(port, host);
}
```

No HTTP listener is added when SSL is active — TLS and plain HTTP listeners are
mutually exclusive at the factory level.

---

## Client Authentication

| `server.ssl.client-auth` | Behaviour |
|---|---|
| _(unset)_ | No client certificate requested |
| `want` | Server requests a cert; client may omit it |
| `need` | Server requires a cert; handshake fails without one |

---

## Test Coverage

Unit tests for `SslBuilderCustomizer` live in:

```
src/test/java/biz/example/web/undertow/SslBuilderCustomizerTests.java
```

Each test starts a real Undertow HTTPS listener on port 0 and drives it with
the JDK `HttpClient`.  The tests cover:

- Basic HTTPS connectivity (server responds `200 OK`)
- Bind-address handling (`null` → all interfaces, explicit loopback)
- `ClientAuth.NONE` and `ClientAuth.WANT` — unauthenticated client succeeds
- `ClientAuth.NEED` — unauthenticated client is rejected with `SSLHandshakeException`
- `ClientAuth.NEED` — mutual-TLS client presenting a valid cert succeeds
- Broken `SslBundle` surfaces as `IllegalStateException`

PEM test fixtures are located under `src/test/resources/ssl/`.
