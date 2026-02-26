package biz.example.web.undertow;

import io.undertow.servlet.api.DeploymentInfo;

/**
 * Strategy interface for customizing Undertow {@link DeploymentInfo}.
 * * In a gRPC-Servlet context, this allows for fine-grained control over 
 * the Servlet context before the DeploymentManager is initialized.
 * * @author Your Name/Research Lead
 * @since 1.0.0
 * @see UndertowServletWebServerFactory
 */
@FunctionalInterface
public interface UndertowDeploymentInfoCustomizer {

    /**
     * Customize the given {@link DeploymentInfo}.
     * @param deploymentInfo the deployment info to customize
     */
    void customize(DeploymentInfo deploymentInfo);

}