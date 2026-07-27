package com.ewos.integration.infrastructure.adapter;

import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapter;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Writes the exchange record as a single-sheet {@code .xlsx} into the configuration's {@code
 * outputDirectory}, using {@link XlsxWriter} (no Apache POI dependency available offline — see
 * Sprint 14.4 Implementation Report). {@code config_json}: {@code {"outputDirectory": "..."}}.
 */
@Component
public class ExcelIntegrationAdapter implements IntegrationAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BusinessErrorClassifier classifier;

    public ExcelIntegrationAdapter(BusinessErrorClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public IntegrationAdapterType type() {
        return IntegrationAdapterType.EXCEL;
    }

    @Override
    public IntegrationAdapterResult execute(IntegrationExecutionContext context) {
        try {
            Path dir = OutputDirectories.resolve(JSON, context.configJson());
            Path file = dir.resolve(safeFileName(context.correlationId()) + ".xlsx");
            byte[] bytes =
                    XlsxWriter.write(
                            List.of(
                                    List.of("correlationId", "exchangeType", "payload"),
                                    List.of(
                                            nullToEmpty(context.correlationId()),
                                            nullToEmpty(context.exchangeType()),
                                            nullToEmpty(context.payloadJson()))));
            Files.write(file, bytes);
            return IntegrationAdapterResult.success("Wrote " + file);
        } catch (IllegalArgumentException e) {
            return IntegrationAdapterResult.failure(
                    ErrorClassification.CONFIGURATION, e.getMessage());
        } catch (IOException e) {
            return IntegrationAdapterResult.failure(classifier.classify(e), e.getMessage());
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String safeFileName(String correlationId) {
        return correlationId == null ? "record" : correlationId.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
