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

import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.Headers;

import org.springframework.boot.web.server.Compression;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * {@link UndertowWebServer.HttpHandlerFactory} that applies HTTP response compression.
 *
 * @author Andy Wilkinson
 */
class CompressionHttpHandlerFactory implements UndertowWebServer.HttpHandlerFactory {

	private final Compression compression;

	CompressionHttpHandlerFactory(Compression compression) {
		this.compression = compression;
	}

	@Override
	public HttpHandler getHandler(HttpHandler next) {
		ContentEncodingRepository repository = new ContentEncodingRepository();
		repository.addEncodingHandler("gzip", new GzipEncodingProvider(), 50,
				Predicates.and(minSizePredicate(), mimeTypePredicate()));
		return new EncodingHandler(repository).setNext(next);
	}

	private Predicate minSizePredicate() {
		long minResponseSize = this.compression.getMinResponseSize().toBytes();
		return (exchange) -> {
			String contentLength = exchange.getResponseHeaders().getFirst(Headers.CONTENT_LENGTH);
			if (contentLength == null) {
				return true;
			}
			try {
				return Long.parseLong(contentLength) >= minResponseSize;
			}
			catch (NumberFormatException ex) {
				return true;
			}
		};
	}

	private Predicate mimeTypePredicate() {
		String[] mimeTypes = this.compression.getMimeTypes();
		if (mimeTypes == null || mimeTypes.length == 0) {
			return Predicates.truePredicate();
		}
		return (exchange) -> {
			String contentType = exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
			if (contentType == null) {
				return false;
			}
			try {
				MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);
				for (String candidate : mimeTypes) {
					if (mimeType.isCompatibleWith(MimeTypeUtils.parseMimeType(candidate))) {
						return true;
					}
				}
			}
			catch (Exception ex) {
				// Unparseable content type — skip compression
			}
			return false;
		};
	}

}
