package com.ewos.tenancy.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantAccessGrantService;
import com.ewos.tenancy.application.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TenantHeaderValidationFilterTest {

    private static final String HEADER = "X-Tenant-Id";

    @Mock TenantContext tenantContext;
    @Mock TenantAccessGrantService grantService;

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
    private final FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

    private TenantHeaderValidationFilter filter() {
        return new TenantHeaderValidationFilter(tenantContext, grantService, om);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), "n/a", java.util.List.of()));
    }

    @Test
    void skipsValidationWhenHeaderIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsValidationWhenUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMalformedHeaderValue() throws Exception {
        authenticate();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentAsString()).contains("not a valid UUID");
    }

    @Test
    void allowsHeaderMatchingHomeTenant() throws Exception {
        authenticate();
        UUID tenantId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsHeaderForDifferentTenantWithActiveGrant() throws Exception {
        authenticate();
        UUID homeTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(homeTenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(userId));
        when(grantService.hasActiveGrant(userId, otherTenantId)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, otherTenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsHeaderForDifferentTenantWithoutGrant() throws Exception {
        authenticate();
        UUID homeTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(homeTenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(userId));
        when(grantService.hasActiveGrant(userId, otherTenantId)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, otherTenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).contains("do not have access");
    }

    @Test
    void propagatesTenantContextFailureAsSameStatus() throws Exception {
        authenticate();
        when(tenantContext.homeTenantId())
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "No tenant is resolved"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).contains("No tenant is resolved");
    }
}
