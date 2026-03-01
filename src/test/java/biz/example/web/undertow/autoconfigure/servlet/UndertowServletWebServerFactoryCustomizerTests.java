package biz.example.web.undertow.autoconfigure.servlet;

import org.junit.jupiter.api.Test;

import biz.example.web.undertow.autoconfigure.UndertowServerProperties;
import biz.example.web.undertow.servlet.UndertowServletWebServerFactory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link UndertowServletWebServerFactoryCustomizer}.
 *
 * Verifies that both servlet-only properties are forwarded to the factory:
 * - {@code eagerFilterInit} → {@link UndertowServletWebServerFactory#setEagerFilterInit}
 * - {@code preservePathOnForward} → {@link UndertowServletWebServerFactory#setPreservePathOnForward}
 */
class UndertowServletWebServerFactoryCustomizerTests {

    @Test
    void eagerFilterInitFalseIsMappedToFactory() {
        UndertowServletWebServerFactory factory = mock(UndertowServletWebServerFactory.class);
        UndertowServerProperties properties = new UndertowServerProperties();
        properties.setEagerFilterInit(false);

        new UndertowServletWebServerFactoryCustomizer(properties).customize(factory);

        verify(factory).setEagerFilterInit(false);
    }

    @Test
    void eagerFilterInitTrueIsMappedToFactory() {
        UndertowServletWebServerFactory factory = mock(UndertowServletWebServerFactory.class);
        UndertowServerProperties properties = new UndertowServerProperties();
        properties.setEagerFilterInit(true);

        new UndertowServletWebServerFactoryCustomizer(properties).customize(factory);

        verify(factory).setEagerFilterInit(true);
    }

    @Test
    void preservePathOnForwardTrueIsMappedToFactory() {
        UndertowServletWebServerFactory factory = mock(UndertowServletWebServerFactory.class);
        UndertowServerProperties properties = new UndertowServerProperties();
        properties.setPreservePathOnForward(true);

        new UndertowServletWebServerFactoryCustomizer(properties).customize(factory);

        verify(factory).setPreservePathOnForward(true);
    }

    @Test
    void preservePathOnForwardFalseIsMappedToFactory() {
        UndertowServletWebServerFactory factory = mock(UndertowServletWebServerFactory.class);
        UndertowServerProperties properties = new UndertowServerProperties();
        properties.setPreservePathOnForward(false);

        new UndertowServletWebServerFactoryCustomizer(properties).customize(factory);

        verify(factory).setPreservePathOnForward(false);
    }

}
