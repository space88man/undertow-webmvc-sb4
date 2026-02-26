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
 * @author Andy Wilkinson
 */
final class GracefulShutdown {

    private static final Log logger = LogFactory.getLog(GracefulShutdown.class);

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
            }
            else if (idle) {
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

    void abort() {
        this.aborted = true;
    }

}
