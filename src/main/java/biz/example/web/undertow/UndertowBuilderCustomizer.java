package biz.example.web.undertow;

import io.undertow.Undertow.Builder;

/**
 * Callback interface that can be used to customize an Undertow {@link Builder}.
 *
 * @author Andy Wilkinson
 * @since 2.0.0
 * @see UndertowServletWebServerFactory
 */
@FunctionalInterface
public interface UndertowBuilderCustomizer {

	/**
	 * Customize the builder.
	 * @param builder the {@code Builder} to customize
	 */
	void customize(Builder builder);

}
