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

package biz.example.web.undertow.autoconfigure;

import io.undertow.servlet.api.DeploymentInfo;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import biz.example.web.undertow.servlet.UndertowServletWebServerFactory;

/**
 * {@link WebServerFactoryCustomizer} to configure virtual threads for an Undertow
 * servlet web server.
 * <p>
 * Unlike Tomcat and Jetty, Undertow virtual threads are applied via
 * {@link DeploymentInfo#setExecutor(java.util.concurrent.Executor)} rather than at the
 * factory level.
 *
 * @author Andy Wilkinson
 * @since 4.0.0
 */
public class UndertowVirtualThreadsWebServerFactoryCustomizer
		implements WebServerFactoryCustomizer<UndertowServletWebServerFactory> {

	@Override
	public void customize(UndertowServletWebServerFactory factory) {
		factory.addDeploymentInfoCustomizers(
				(deploymentInfo) -> deploymentInfo.setExecutor(new VirtualThreadTaskExecutor("undertow-")));
	}

}
