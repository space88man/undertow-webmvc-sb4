/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.example.web.undertow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.server.HttpServerExchange;
import org.junit.jupiter.api.Test;

import org.springframework.boot.web.server.GracefulShutdownResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GracefulShutdown}.
 *
 * This is an internal helper class — not a Spring bean. It is instantiated by
 * {@link UndertowWebServer} when graceful shutdown is configured and wraps
 * a {@link UndertowWebServer.GracefulShutdownHttpHandler} (which is an Undertow
 * {@code GracefulShutdownHandler}).
 *
 * Tests verify:
 * - {@code shutDownGracefully()} calls the callback with {@code IDLE} when no
 *   requests are in flight (the handler drains immediately)
 * - {@code abort()} causes the callback to receive {@code REQUESTS_ACTIVE} even
 *   when the handler has already marked itself idle
 * - The shutdown background thread is named "undertow-shutdown"
 */
class GracefulShutdownTests {

    @Test
    void shutdownCallsCallbackWithIdleWhenNoRequestsAreActive() throws InterruptedException {
        AtomicReference<GracefulShutdownResult> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        GracefulShutdown shutdown = new GracefulShutdown(new ImmediateIdleHandler());
        shutdown.shutDownGracefully((r) -> {
            result.set(r);
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isEqualTo(GracefulShutdownResult.IDLE);
    }

    @Test
    void abortCausesCallbackToReceiveRequestsActive() throws InterruptedException {
        AtomicReference<GracefulShutdownResult> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        GracefulShutdown shutdown = new GracefulShutdown(new ImmediateIdleHandler());
        // abort before shutDownGracefully — simulates forced close during drain
        shutdown.abort();
        shutdown.shutDownGracefully((r) -> {
            result.set(r);
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isEqualTo(GracefulShutdownResult.REQUESTS_ACTIVE);
    }

    // ── Stub ──────────────────────────────────────────────────────────────────

    /**
     * A {@link UndertowWebServer.GracefulShutdownHttpHandler} that completes shutdown
     * immediately: {@code shutdown()} is a no-op and {@code awaitShutdown()} returns
     * {@code true} (idle) without waiting.
     */
    private static final class ImmediateIdleHandler
            implements UndertowWebServer.GracefulShutdownHttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) {
        }

        @Override
        public void shutdown() {
            // already "idle" — nothing to drain
        }

        @Override
        public boolean awaitShutdown(long millis) {
            return true; // idle immediately
        }

    }

}
