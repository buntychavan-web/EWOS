package com.ewos.security.cors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

    @Test
    void wildcardOriginRejectedInProdProfile() {
        CorsProperties props = defaultProps();
        props.setAllowedOrigins(List.of("*"));
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new CorsConfig(props, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard")
                .hasMessageContaining("prod");
    }

    @Test
    void wildcardOriginPatternRejectedInProdProfile() {
        CorsProperties props = defaultProps();
        props.setAllowedOrigins(List.of());
        props.setAllowedOriginPatterns(List.of("*"));
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new CorsConfig(props, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard")
                .hasMessageContaining("prod");
    }

    @Test
    void wildcardOriginWithCredentialsRejectedEvenInDev() {
        CorsProperties props = defaultProps();
        props.setAllowedOrigins(List.of("*"));
        props.setAllowCredentials(true);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        assertThatThrownBy(() -> new CorsConfig(props, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard")
                .hasMessageContaining("allowCredentials");
    }

    @Test
    void wildcardOriginAllowedInDevWhenCredentialsOff() {
        CorsProperties props = defaultProps();
        props.setAllowedOrigins(List.of("*"));
        props.setAllowCredentials(false);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        CorsConfig config = new CorsConfig(props, env);
        assertThat(config.corsConfigurationSource())
                .isInstanceOf(UrlBasedCorsConfigurationSource.class);
    }

    @Test
    void configuredOriginsBecomePartOfSource() {
        CorsProperties props = defaultProps();
        props.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource)
                        new CorsConfig(props, env).corsConfigurationSource();

        // We know we registered "/**" — read that pattern back out.
        CorsConfiguration cors = source.getCorsConfigurations().get("/**");
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "http://127.0.0.1:5173");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(cors.getExposedHeaders()).contains("X-Request-ID");
    }

    @Test
    void emptyOriginsInProdIsAllowedButLogsWarning() {
        CorsProperties props = defaultProps();
        props.setAllowedOrigins(List.of());
        props.setAllowedOriginPatterns(List.of());
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        // No exception — deny-by-default is a valid production posture.
        CorsConfig config = new CorsConfig(props, env);
        assertThat(config.corsConfigurationSource()).isNotNull();
    }

    private CorsProperties defaultProps() {
        CorsProperties p = new CorsProperties();
        p.setAllowedOrigins(List.of("http://localhost:5173"));
        p.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        p.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
        p.setExposedHeaders(List.of("X-Request-ID"));
        p.setAllowCredentials(true);
        p.setMaxAge(Duration.ofHours(1));
        p.setPathPatterns(List.of("/**"));
        return p;
    }
}
