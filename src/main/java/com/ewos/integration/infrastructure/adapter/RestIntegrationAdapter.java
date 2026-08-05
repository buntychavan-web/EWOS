package com.ewos.integration.infrastructure.adapter;

import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapter;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Posts the payload JSON to a configured HTTP endpoint. {@code config_json}: {@code {"url":
 * "https://...", "method": "POST"}} — {@code method} defaults to {@code POST}. Any non-2xx response
 * or transport failure is classified via {@link BusinessErrorClassifier}.
 */
@Component
public class RestIntegrationAdapter implements IntegrationAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final BusinessErrorClassifier classifier;

    public RestIntegrationAdapter(
            RestTemplate integrationRestTemplate, BusinessErrorClassifier classifier) {
        this.restTemplate = integrationRestTemplate;
        this.classifier = classifier;
    }

    @Override
    public IntegrationAdapterType type() {
        return IntegrationAdapterType.REST;
    }

    @Override
    public IntegrationAdapterResult execute(IntegrationExecutionContext context) {
        String url;
        HttpMethod method;
        try {
            JsonNode config = readConfig(context.configJson());
            url = requireText(config, "url");
            OutboundUrlGuard.assertSafe(url);
            method =
                    config.has("method")
                            ? HttpMethod.valueOf(config.get("method").asText())
                            : HttpMethod.POST;
        } catch (IllegalArgumentException e) {
            return IntegrationAdapterResult.failure(
                    ErrorClassification.CONFIGURATION, e.getMessage());
        }

        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<String> entity =
                new HttpEntity<>(
                        context.payloadJson() == null ? "{}" : context.payloadJson(), headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, method, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return IntegrationAdapterResult.success(
                        "HTTP " + response.getStatusCode().value() + " from " + url);
            }
            return IntegrationAdapterResult.failure(
                    ErrorClassification.EXTERNAL_SYSTEM,
                    "HTTP " + response.getStatusCode().value() + " from " + url);
        } catch (RestClientException e) {
            return IntegrationAdapterResult.failure(classifier.classify(e), e.getMessage());
        }
    }

    private static JsonNode readConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("Integration configuration is missing config_json");
        }
        try {
            return JSON.readTree(configJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Integration configuration config_json is not valid JSON", e);
        }
    }

    private static String requireText(JsonNode config, String field) {
        JsonNode node = config.get(field);
        if (node == null || node.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Integration configuration is missing '" + field + "'");
        }
        return node.asText();
    }
}
