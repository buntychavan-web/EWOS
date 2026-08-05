package com.ewos.integration.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboundUrlGuardTest {

    @Test
    void rejectsCloudMetadataAddress() {
        assertThatThrownBy(
                        () ->
                                OutboundUrlGuard.assertSafe(
                                        "http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLoopback() {
        assertThatThrownBy(() -> OutboundUrlGuard.assertSafe("http://127.0.0.1:8080/actuator/env"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrlGuard.assertSafe("http://localhost/actuator/env"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrivateRfc1918Ranges() {
        assertThatThrownBy(() -> OutboundUrlGuard.assertSafe("http://10.0.0.5/internal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrlGuard.assertSafe("http://172.16.0.5/internal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboundUrlGuard.assertSafe("http://192.168.1.5/internal"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> OutboundUrlGuard.assertSafe("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> OutboundUrlGuard.assertSafe("not a url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsAPublicHttpsHost() {
        assertThatCode(() -> OutboundUrlGuard.assertSafe("https://8.8.8.8/resolve"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsAHostnameThatDoesNotResolveInThisEnvironment() {
        // Unresolvable is not evidence of an unsafe target — the real HTTP call surfaces its own
        // error. This also matches how RestIntegrationAdapterTest exercises example.com-style
        // hostnames against MockRestServiceServer without real DNS ever succeeding.
        assertThatCode(() -> OutboundUrlGuard.assertSafe("https://partner.example.com/hook"))
                .doesNotThrowAnyException();
    }
}
