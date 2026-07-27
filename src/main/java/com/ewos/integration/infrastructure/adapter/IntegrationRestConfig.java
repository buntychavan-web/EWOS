package com.ewos.integration.infrastructure.adapter;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Dedicated {@link RestTemplate} for {@link RestIntegrationAdapter} — bounded timeouts so a slow or
 * unreachable external system can't hang an integration execution indefinitely.
 */
@Configuration
public class IntegrationRestConfig {

    @Bean
    public RestTemplate integrationRestTemplate(RestTemplateBuilder builder) {
        return builder.setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
