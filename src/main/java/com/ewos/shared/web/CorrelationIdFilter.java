package com.ewos.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every request has a stable correlation id.
 *
 * <ul>
 *   <li>Reads the inbound {@code X-Request-ID} header, or mints a UUID.
 *   <li>Publishes the id on the response so callers can correlate their side.
 *   <li>Puts the id in the SLF4J MDC under {@code correlationId} so every log line for the request
 *       carries it.
 * </ul>
 *
 * Registered with {@link Ordered#HIGHEST_PRECEDENCE} so it runs before the Spring Security filter
 * chain — that way even 401 / 403 responses carry the header and MDC values.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String correlationId =
                StringUtils.hasText(inbound) ? inbound : UUID.randomUUID().toString();
        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HEADER, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
