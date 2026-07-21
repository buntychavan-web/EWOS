package com.ewos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesInboundCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(CorrelationIdFilter.HEADER, "abcd-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain =
                (req, res) ->
                        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("abcd-1234");
        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abcd-1234");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mintsCorrelationIdWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain =
                (req, res) ->
                        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotNull().isNotBlank();
        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mdcIsClearedEvenOnDownstreamException() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain =
                (req, res) -> {
                    throw new RuntimeException("boom");
                };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
            // expected — we just care that MDC is cleaned up
        }
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
