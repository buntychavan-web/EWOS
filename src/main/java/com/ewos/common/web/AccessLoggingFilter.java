package com.ewos.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One access-log line per HTTP request: method, path, status, duration, user id (if any),
 * correlation id, client IP.
 *
 * <p>Runs late in the chain so status codes reflect the final decision. All fields are also placed
 * into MDC while the request is in flight so downstream log lines inherit the same enrichment; MDC
 * keys are stripped in a finally block.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class AccessLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLoggingFilter.class);

    private static final String MDC_METHOD = "httpMethod";
    private static final String MDC_PATH = "httpPath";
    private static final String MDC_STATUS = "httpStatus";
    private static final String MDC_USER = "userId";
    private static final String MDC_IP = "clientIp";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String ip = clientIp(request);
        try {
            MDC.put(MDC_METHOD, method);
            MDC.put(MDC_PATH, path);
            MDC.put(MDC_IP, ip);
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            int status = response.getStatus();
            String userId = currentUserId();
            MDC.put(MDC_STATUS, Integer.toString(status));
            if (userId != null) {
                MDC.put(MDC_USER, userId);
            }
            log.info(
                    "access method={} path={} status={} duration_ms={} user={} ip={}",
                    method,
                    path,
                    status,
                    durationMs,
                    userId == null ? "-" : userId,
                    ip);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
            MDC.remove(MDC_STATUS);
            MDC.remove(MDC_USER);
            MDC.remove(MDC_IP);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        return (name == null || name.isBlank() || "anonymousUser".equals(name)) ? null : name;
    }
}
