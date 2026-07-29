package com.ewos.identity.infrastructure.security;

import com.ewos.identity.infrastructure.security.jwt.JwtAuthenticationFilter;
import com.ewos.shared.exception.ApiError;
import com.ewos.tenancy.infrastructure.security.TenantHeaderValidationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            TenantHeaderValidationFilter tenantHeaderValidationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint))
                // Spring Security's defaults already cover X-Content-Type-Options, X-Frame-Options,
                // Strict-Transport-Security (HTTPS requests), and Cache-Control — none of that is
                // touched here. CSP and Referrer-Policy are the two headers Spring Security does
                // not
                // set out of the box, and this backend does serve one same-origin HTML surface
                // (Swagger UI at /swagger-ui/**) where they matter; 'unsafe-inline' on script/style
                // is required by springdoc's bundled Swagger UI bootstrap, not a broad exception —
                // there is no other HTML rendered by this API.
                .headers(
                        headers ->
                                headers.contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; "
                                                                        + "script-src 'self' 'unsafe-inline'; "
                                                                        + "style-src 'self' 'unsafe-inline'; "
                                                                        + "img-src 'self' data:; "
                                                                        + "frame-ancestors 'none'"))
                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .NO_REFERRER)))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_ENDPOINTS)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantHeaderValidationFilter, JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String message =
                    authException.getMessage() != null
                            ? authException.getMessage()
                            : "Authentication required";
            ApiError body = ApiError.of(401, "Unauthorized", message, request.getRequestURI());
            objectMapper.writeValue(response.getWriter(), body);
        };
    }
}
