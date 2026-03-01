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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.web.server.GracefulShutdownCallback;
import org.springframework.boot.web.server.GracefulShutdownResult;

/**
 * Handles Undertow graceful shutdown.
 *
 * <p>Shutdown order is critical for avoiding XNIO007007
 * ({@code RejectedExecutionException: Thread is terminating}) during WebSocket
 * session close. The JSR-356 {@code UndertowSession.close0()} dispatches onto
 * an XNIO worker thread. If the XNIO worker pool is torn down before the servlet
 * deployment is undeployed, every in-flight {@code @OnClose} callback throws
 * {@code RejectedExecutionException}.
 *
 * <p>The correct teardown order is:
 * <ol>
 *   <li>Signal the handler to stop accepting new requests ({@link UndertowWebServer.GracefulShutdownHttpHandler#shutdown()})</li>
 *   <li>Wait for in-flight HTTP/WebSocket requests to drain</li>
 *   <li>Allow a brief quiesce for JSR-356 {@code @OnClose} dispatches to complete</li>
 *   <li>Undeploy the servlet deployment (JSR-356 container teardown)</li>
 *   <li>Stop the XNIO worker ({@code undertow.stop()})</li>
 * </ol>
 *
 * <p>Steps 4 and 5 are the responsibility of {@link biz.example.web.undertow.servlet.UndertowServletWebServer#stop()},
 * which must call {@code manager.undeploy()} before {@code super.stop()}.
 *
 * @author Andy Wilkinson
 */
final class GracefulShutdown {

    private static final Log logger = LogFactory.getLog(GracefulShutdown.class);

    /**
     * Brief pause after the handler reports idle to allow any in-progress
     * JSR-356 {@code @OnClose} dispatches to complete on XNIO worker threads
     * before the XNIO worker pool is torn down.
     * 200 ms is sufficient — {@code @OnClose} callbacks are synchronous and fast.
     */
    private static final long WEBSOCKET_QUIESCE_MS = 200L;

    private final UndertowWebServer.GracefulShutdownHttpHandler handler;

    private volatile boolean aborted;

    GracefulShutdown(UndertowWebServer.GracefulShutdownHttpHandler handler) {
        this.handler = handler;
    }

    void shutDownGracefully(GracefulShutdownCallback callback) {
        logger.info("Commencing graceful shutdown. Waiting for active requests to complete");
        this.handler.shutdown();
        new Thread(() -> awaitShutdown(callback), "undertow-shutdown").start();
    }

    private void awaitShutdown(GracefulShutdownCallback callback) {
        try {
            boolean idle = this.handler.awaitShutdown(Long.MAX_VALUE);
            if (this.aborted) {
                logger.info("Graceful shutdown aborted with one or more requests still active");
                callback.shutdownComplete(GracefulShutdownResult.REQUESTS_ACTIVE);
                return;
            }
            if (idle) {
                // Quiesce: allow JSR-356 @OnClose dispatches to complete on XNIO
                // worker threads before UndertowServletWebServer.stop() tears the
                // worker pool down. Without this pause, UndertowSession.close0()
                // throws RejectedExecutionException (XNIO007007).
                quiesceWebSocketSessions();
                logger.info("Graceful shutdown complete");
                callback.shutdownComplete(GracefulShutdownResult.IDLE);
            }
            else {
                callback.shutdownComplete(GracefulShutdownResult.REQUESTS_ACTIVE);
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            callback.shutdownComplete(GracefulShutdownResult.REQUESTS_ACTIVE);
        }
    }

    /**
     * Brief pause to allow in-progress JSR-356 WebSocket {@code @OnClose}
     * dispatches to complete on XNIO worker threads.
     *
     * <p>This is necessary because {@code GracefulShutdownHttpHandler.awaitShutdown()}
     * reports idle as soon as the last HTTP exchange completes — but a WebSocket
     * {@code @OnClose} callback may still be dispatched asynchronously onto an XNIO
     * worker thread at that point. If {@code undertow.stop()} races ahead and
     * terminates the worker pool, {@code WorkerThread.execute()} throws
     * {@code RejectedExecutionException: XNIO007007}.
     */
    private void quiesceWebSocketSessions() throws InterruptedException {
        Thread.sleep(WEBSOCKET_QUIESCE_MS);
    }

    void abort() {
        this.aborted = true;
    }

}
