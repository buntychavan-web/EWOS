package com.ewos.integration.infrastructure.adapter;

import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapter;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Writes the raw payload JSON verbatim into the configuration's {@code outputDirectory} — the
 * "drop a file for manual pickup" transport, distinct from CSV/EXCEL which reshape the payload
 * into tabular form. {@code config_json}: {@code {"outputDirectory": "..."}}.
 */
@Component
public class FileUploadIntegrationAdapter implements IntegrationAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BusinessErrorClassifier classifier;

    public FileUploadIntegrationAdapter(BusinessErrorClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public IntegrationAdapterType type() {
        return IntegrationAdapterType.FILE_UPLOAD;
    }

    @Override
    public IntegrationAdapterResult execute(IntegrationExecutionContext context) {
        try {
            Path dir = OutputDirectories.resolve(JSON, context.configJson());
            Path file = dir.resolve(safeFileName(context.correlationId()) + ".json");
            Files.writeString(
                    file, context.payloadJson() == null ? "{}" : context.payloadJson(), StandardCharsets.UTF_8);
            return IntegrationAdapterResult.success("Wrote " + file);
        } catch (IllegalArgumentException e) {
            return IntegrationAdapterResult.failure(ErrorClassification.CONFIGURATION, e.getMessage());
        } catch (IOException e) {
            return IntegrationAdapterResult.failure(classifier.classify(e), e.getMessage());
        }
    }

    private static String safeFileName(String correlationId) {
        return correlationId == null
                ? "record"
                : correlationId.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
