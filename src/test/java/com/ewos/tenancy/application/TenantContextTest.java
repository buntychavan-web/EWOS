package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class TenantContextTest {

    private static final String TENANT_ID_REQUEST_ATTRIBUTE = "com.ewos.tenancy.currentTenantId";

    private final TenantContext tenantContext = new TenantContext();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void homeTenantIdReturnsTenantPublishedOnTheRequestByTheJwtFilter() {
        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TENANT_ID_REQUEST_ATTRIBUTE, tenantId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(tenantContext.homeTenantId()).isEqualTo(tenantId);
    }

    @Test
    void homeTenantIdRejectsRequestWithNoResolvedTenant() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThatThrownBy(tenantContext::homeTenantId).isInstanceOf(ApiException.class);
    }

    @Test
    void homeTenantIdRejectsWhenNoRequestContextExists() {
        assertThatThrownBy(tenantContext::homeTenantId).isInstanceOf(ApiException.class);
    }

    @Test
    void currentUserIdReadsAuthenticationName() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userId.toString(), "n/a", java.util.List.of()));

        assertThat(tenantContext.currentUserId()).contains(userId);
    }

    @Test
    void currentUserIdEmptyWhenUnauthenticated() {
        assertThat(tenantContext.currentUserId()).isEmpty();
    }

    @Test
    void currentUserIdEmptyWhenAuthenticationNameIsNotAUuid() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "not-a-uuid", "n/a", java.util.List.of()));

        assertThat(tenantContext.currentUserId()).isEmpty();
    }
}
