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

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.DefaultAccessLogReceiver;
import org.jspecify.annotations.Nullable;

/**
 * {@link UndertowWebServer.HttpHandlerFactory} that adds Undertow access logging.
 *
 * @author Andy Wilkinson
 */
class AccessLogHttpHandlerFactory implements UndertowWebServer.HttpHandlerFactory {

	private static final String DEFAULT_LOG_FORMAT = "common";

	private static final String DEFAULT_PREFIX = "access_log.";

	private static final String DEFAULT_SUFFIX = "log";

	private final @Nullable File directory;

	private final @Nullable String pattern;

	private final @Nullable String prefix;

	private final @Nullable String suffix;

	private final boolean rotate;

	AccessLogHttpHandlerFactory(@Nullable File directory, @Nullable String pattern,
			@Nullable String prefix, @Nullable String suffix, boolean rotate) {
		this.directory = directory;
		this.pattern = pattern;
		this.prefix = prefix;
		this.suffix = suffix;
		this.rotate = rotate;
	}

	@Override
	public HttpHandler getHandler(HttpHandler next) {
		File logDirectory = (this.directory != null) ? this.directory : new File("logs");
		logDirectory.mkdirs();
		String logPattern = (this.pattern != null) ? this.pattern : DEFAULT_LOG_FORMAT;
		String logPrefix = (this.prefix != null) ? this.prefix : DEFAULT_PREFIX;
		String logSuffix = (this.suffix != null) ? this.suffix : DEFAULT_SUFFIX;
		DefaultAccessLogReceiver receiver = DefaultAccessLogReceiver.builder()
			.setOutputDirectory(logDirectory.toPath())
			.setLogBaseName(logPrefix)
			.setLogNameSuffix(logSuffix)
			.setRotate(this.rotate)
			.build();
		return new AccessLogHandler(next, receiver, logPattern,
				AccessLogHttpHandlerFactory.class.getClassLoader());
	}

}
