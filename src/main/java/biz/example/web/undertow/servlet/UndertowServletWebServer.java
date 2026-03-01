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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

	private static final Log logger = LogFactory.getLog(UndertowServletWebServer.class);

	/**
	 * Brief pause between servlet undeploy and XNIO worker teardown.
	 *
	 * <p>After {@code manager.undeploy()} the XNIO I/O threads are still alive and
	 * may still receive a WebSocket CLOSE frame from a connected client. That frame
	 * triggers {@code FrameHandler.onFullCloseMessage} which dispatches the JSR-356
	 * {@code @OnClose} callback onto a worker thread. If {@code undertow.stop()} races
	 * ahead and terminates the worker pool first, the dispatch throws
	 * {@code RejectedExecutionException: XNIO007007}.
	 *
	 * <p>A 100 ms quiesce gives in-flight WebSocket close handshakes time to complete
	 * on the worker while it is still alive. This mirrors the quiesce in
	 * {@link biz.example.web.undertow.GracefulShutdown} which covers the graceful
	 * shutdown path; this constant covers the direct {@link #stop()} path.
	 */
	private static final long WEBSOCKET_CLOSE_QUIESCE_MS = 100L;

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
		if (!isStarted()) {
			return;
		}
		if (this.manager != null) {
			// Undeploy BEFORE stopping the XNIO worker. JSR-356 UndertowSession.close0()
			// dispatches @OnClose callbacks onto XNIO worker threads. If undertow.stop()
			// runs first, those dispatches throw RejectedExecutionException (XNIO007007).
			try {
				this.manager.undeploy();
			}
			catch (Exception ex) {
				// Must not prevent XNIO teardown — log and continue.
				logger.warn("Exception during servlet deployment undeploy", ex);
			}
			try {
				this.manager.stop();
			}
			catch (Exception ex) {
				logger.warn("Exception during deployment manager stop", ex);
			}
			// Allow in-flight WebSocket CLOSE frames (sent by clients) to be processed
			// on XNIO worker threads before undertow.stop() terminates the worker pool.
			try {
				Thread.sleep(WEBSOCKET_CLOSE_QUIESCE_MS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		// Tear down the XNIO worker last — after all servlet sessions are closed.
		super.stop();
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
