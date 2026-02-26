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

package biz.example.web.undertow.servlet;

import io.undertow.Handlers;
import io.undertow.Undertow.Builder;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.api.DeploymentManager;
import org.jspecify.annotations.Nullable;

import biz.example.web.undertow.UndertowWebServer;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.util.StringUtils;

/**
 * {@link biz.example.web.undertow.UndertowWebServer} for the servlet stack. Extends the
 * base server with context-path prefixing and access to the {@link DeploymentManager}.
 *
 * @author Ivan Sopov
 * @author Andy Wilkinson
 * @author Eddú Meléndez
 * @author Christoph Dreis
 * @author Kristine Jetzke
 * @since 4.0.0
 * @see UndertowServletWebServerFactory
 */
public class UndertowServletWebServer extends UndertowWebServer {

	private final String contextPath;

	private final @Nullable DeploymentManager manager;

	/**
	 * Create a new {@link UndertowServletWebServer} instance.
	 * @param builder the Undertow builder
	 * @param httpHandlerFactories the handler factories
	 * @param contextPath the root context path
	 * @param autoStart whether the server should start automatically
	 */
	public UndertowServletWebServer(Builder builder, Iterable<HttpHandlerFactory> httpHandlerFactories,
			String contextPath, boolean autoStart) {
		super(builder, httpHandlerFactories, autoStart);
		this.contextPath = contextPath;
		this.manager = findManager(httpHandlerFactories);
	}

	private @Nullable DeploymentManager findManager(Iterable<HttpHandlerFactory> httpHandlerFactories) {
		for (HttpHandlerFactory factory : httpHandlerFactories) {
			if (factory instanceof DeploymentManagerHttpHandlerFactory deploymentManagerFactory) {
				return deploymentManagerFactory.getDeploymentManager();
			}
		}
		return null;
	}

	@Override
	protected HttpHandler createHttpHandler() {
		HttpHandler handler = super.createHttpHandler();
		if (StringUtils.hasLength(this.contextPath)) {
			handler = Handlers.path().addPrefixPath(this.contextPath, handler);
		}
		return handler;
	}

	@Override
	protected String getStartLogMessage() {
		String contextPath = StringUtils.hasText(this.contextPath) ? this.contextPath : "/";
		return super.getStartLogMessage() + " with context path '" + contextPath + "'";
	}

	@Override
	public void stop() throws WebServerException {
		super.stop();
		if (this.manager != null) {
			try {
				this.manager.stop();
				this.manager.undeploy();
			}
			catch (Exception ex) {
				throw new WebServerException("Failed to undeploy Undertow servlet context", ex);
			}
		}
	}

	/**
	 * Returns the {@link DeploymentManager} for this server, or {@code null} if none
	 * was found in the handler factory chain.
	 * @return the deployment manager
	 */
	public @Nullable DeploymentManager getDeploymentManager() {
		return this.manager;
	}

}
