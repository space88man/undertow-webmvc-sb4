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

import io.undertow.server.HttpHandler;
import io.undertow.servlet.api.DeploymentManager;
import jakarta.servlet.ServletException;

import biz.example.web.undertow.UndertowEmbeddedErrorHandler;
import biz.example.web.undertow.UndertowWebServer;
import org.springframework.boot.web.server.WebServerException;

/**
 * {@link UndertowWebServer.HttpHandlerFactory} that deploys a servlet context via a
 * {@link DeploymentManager} and wraps it with {@link UndertowEmbeddedErrorHandler}.
 *
 * @author Andy Wilkinson
 * @since 4.0.0
 */
class DeploymentManagerHttpHandlerFactory implements UndertowWebServer.HttpHandlerFactory {

	private final DeploymentManager deploymentManager;

	DeploymentManagerHttpHandlerFactory(DeploymentManager deploymentManager) {
		this.deploymentManager = deploymentManager;
	}

	@Override
	public HttpHandler getHandler(HttpHandler next) {
		try {
			this.deploymentManager.deploy();
			return new UndertowEmbeddedErrorHandler(this.deploymentManager.start());
		}
		catch (ServletException ex) {
			throw new WebServerException("Failed to deploy Undertow servlet context", ex);
		}
	}

	DeploymentManager getDeploymentManager() {
		return this.deploymentManager;
	}

}
