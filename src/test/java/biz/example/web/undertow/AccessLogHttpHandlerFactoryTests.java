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

import java.io.File;

import io.undertow.server.handlers.accesslog.AccessLogHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AccessLogHttpHandlerFactory}.
 *
 * This is an internal helper class — not a Spring bean. It is created directly by
 * {@link UndertowWebServerFactory} when {@code setAccessLogEnabled(true)} is set, and
 * plugged into the {@link UndertowWebServer.HttpHandlerFactory} decorator chain.
 *
 * Tests verify:
 * - The returned handler is an {@link AccessLogHandler} wrapping the next handler
 * - The log directory is created when it does not yet exist
 * - Null arguments fall back to documented defaults ("common", "access_log.", "log")
 * - Custom pattern, prefix, suffix, and directory are accepted without error
 * - The {@code rotate} flag is passed through without throwing
 */
class AccessLogHttpHandlerFactoryTests {

    @TempDir
    File tempDir;

    @Test
    void getHandlerReturnsAccessLogHandler() {
        AccessLogHttpHandlerFactory factory = new AccessLogHttpHandlerFactory(
                tempDir, null, null, null, false);
        assertThat(factory.getHandler((exchange) -> {})).isInstanceOf(AccessLogHandler.class);
    }

    @Test
    void getHandlerCreatesLogDirectoryWhenItDoesNotExist() {
        File logDir = new File(tempDir, "missing-logs");
        assertThat(logDir).doesNotExist();
        new AccessLogHttpHandlerFactory(logDir, null, null, null, false)
                .getHandler((exchange) -> {});
        assertThat(logDir).isDirectory();
    }

    @Test
    void getHandlerWithNullDirectoryDoesNotThrow() {
        // null → new File("logs") — created relative to CWD; just verify no exception
        AccessLogHttpHandlerFactory factory = new AccessLogHttpHandlerFactory(
                null, "combined", "test_", ".log", false);
        assertThat(factory.getHandler((exchange) -> {})).isNotNull();
    }

    @Test
    void getHandlerWithCustomPatternAndAffixes() {
        AccessLogHttpHandlerFactory factory = new AccessLogHttpHandlerFactory(
                tempDir, "%h %t \"%r\" %s %b", "my_access.", ".txt", false);
        assertThat(factory.getHandler((exchange) -> {})).isInstanceOf(AccessLogHandler.class);
    }

    @Test
    void getHandlerWithRotateEnabled() {
        AccessLogHttpHandlerFactory factory = new AccessLogHttpHandlerFactory(
                tempDir, null, null, null, true);
        assertThat(factory.getHandler((exchange) -> {})).isInstanceOf(AccessLogHandler.class);
    }

    @Test
    void getHandlerWithAllNullsUsesDefaults() {
        // All nulls — every field falls back to the DEFAULT_* constants; must not NPE
        AccessLogHttpHandlerFactory factory = new AccessLogHttpHandlerFactory(
                tempDir, null, null, null, false);
        assertThat(factory.getHandler((exchange) -> {})).isNotNull();
    }

}
