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
 * Writes the exchange record's correlation id and payload as a single-row CSV file into the
 * configuration's {@code outputDirectory}. {@code config_json}: {@code {"outputDirectory": "..."}}.
 */
@Component
public class CsvIntegrationAdapter implements IntegrationAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BusinessErrorClassifier classifier;

    public CsvIntegrationAdapter(BusinessErrorClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public IntegrationAdapterType type() {
        return IntegrationAdapterType.CSV;
    }

    @Override
    public IntegrationAdapterResult execute(IntegrationExecutionContext context) {
        try {
            Path dir = OutputDirectories.resolve(JSON, context.configJson());
            Path file = dir.resolve(safeFileName(context.correlationId()) + ".csv");
            String csv =
                    "correlationId,exchangeType,payload\n"
                            + escape(context.correlationId())
                            + ","
                            + escape(context.exchangeType())
                            + ","
                            + escape(context.payloadJson())
                            + "\n";
            Files.writeString(file, csv, StandardCharsets.UTF_8);
            return IntegrationAdapterResult.success("Wrote " + file);
        } catch (IllegalArgumentException e) {
            return IntegrationAdapterResult.failure(ErrorClassification.CONFIGURATION, e.getMessage());
        } catch (IOException e) {
            return IntegrationAdapterResult.failure(classifier.classify(e), e.getMessage());
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static String safeFileName(String correlationId) {
        return correlationId == null
                ? "record"
                : correlationId.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
