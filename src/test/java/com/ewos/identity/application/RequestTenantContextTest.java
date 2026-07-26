package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestTenantContextTest {

    private static final String TENANT_ID_REQUEST_ATTRIBUTE = "com.ewos.tenancy.currentTenantId";

    private final RequestTenantContext context = new RequestTenantContext();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void currentTenantIdReturnsTenantPublishedOnTheRequestByTheJwtFilter() {
        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TENANT_ID_REQUEST_ATTRIBUTE, tenantId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(context.currentTenantId()).contains(tenantId);
    }

    @Test
    void currentTenantIdEmptyWhenNoneResolved() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThat(context.currentTenantId()).isEmpty();
    }

    @Test
    void currentTenantIdEmptyWhenNoRequestContextExists() {
        assertThat(context.currentTenantId()).isEmpty();
    }
}
