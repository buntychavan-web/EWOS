package com.ewos.integration.infrastructure.adapter;

import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapter;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Uploads the payload JSON to a configured SFTP drop path via {@link SftpTransport}. {@code
 * config_json}: {@code {"host": "...", "port": 22, "remotePath": "/inbound", "username": "...",
 * "credentialRef": "..."}} — {@code credentialRef} names a secret resolved by the deployment's
 * secret store; the raw credential is never persisted in {@code config_json}.
 */
@Component
public class SftpIntegrationAdapter implements IntegrationAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SftpTransport transport;
    private final BusinessErrorClassifier classifier;

    public SftpIntegrationAdapter(SftpTransport transport, BusinessErrorClassifier classifier) {
        this.transport = transport;
        this.classifier = classifier;
    }

    @Override
    public IntegrationAdapterType type() {
        return IntegrationAdapterType.SFTP;
    }

    @Override
    public IntegrationAdapterResult execute(IntegrationExecutionContext context) {
        SftpTransport.SftpTarget target;
        try {
            target = readTarget(context.configJson());
        } catch (IllegalArgumentException e) {
            return IntegrationAdapterResult.failure(
                    ErrorClassification.CONFIGURATION, e.getMessage());
        }

        String fileName = safeFileName(context.correlationId()) + ".json";
        byte[] content =
                (context.payloadJson() == null ? "{}" : context.payloadJson())
                        .getBytes(StandardCharsets.UTF_8);

        try {
            transport.upload(target, fileName, content);
            return IntegrationAdapterResult.success(
                    "Uploaded " + fileName + " to " + target.host() + ":" + target.remotePath());
        } catch (IOException e) {
            return IntegrationAdapterResult.failure(classifier.classify(e), e.getMessage());
        }
    }

    private static SftpTransport.SftpTarget readTarget(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("Integration configuration is missing config_json");
        }
        JsonNode node;
        try {
            node = JSON.readTree(configJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Integration configuration config_json is not valid JSON", e);
        }
        String host = requireText(node, "host");
        String remotePath = requireText(node, "remotePath");
        String username = node.hasNonNull("username") ? node.get("username").asText() : null;
        String credentialRef =
                node.hasNonNull("credentialRef") ? node.get("credentialRef").asText() : null;
        int port = node.hasNonNull("port") ? node.get("port").asInt() : 22;
        return new SftpTransport.SftpTarget(host, port, username, credentialRef, remotePath);
    }

    private static String requireText(JsonNode config, String field) {
        JsonNode node = config.get(field);
        if (node == null || node.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Integration configuration is missing '" + field + "'");
        }
        return node.asText();
    }

    private static String safeFileName(String correlationId) {
        return correlationId == null ? "record" : correlationId.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
