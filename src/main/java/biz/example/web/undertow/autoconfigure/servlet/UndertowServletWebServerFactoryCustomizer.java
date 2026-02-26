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

package biz.example.web.undertow.autoconfigure.servlet;

import biz.example.web.undertow.autoconfigure.UndertowServerProperties;
import biz.example.web.undertow.servlet.UndertowServletWebServerFactory;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;

/**
 * {@link WebServerFactoryCustomizer} to apply
 * {@link UndertowServerProperties servlet-only Undertow properties} to an
 * {@link UndertowServletWebServerFactory}.
 *
 * @author Andy Wilkinson
 * @since 4.0.0
 */
public class UndertowServletWebServerFactoryCustomizer
		implements WebServerFactoryCustomizer<UndertowServletWebServerFactory> {

	private final UndertowServerProperties undertowProperties;

	public UndertowServletWebServerFactoryCustomizer(UndertowServerProperties undertowProperties) {
		this.undertowProperties = undertowProperties;
	}

	@Override
	public void customize(UndertowServletWebServerFactory factory) {
		factory.setEagerFilterInit(this.undertowProperties.isEagerFilterInit());
		factory.setPreservePathOnForward(this.undertowProperties.isPreservePathOnForward());
	}

}
