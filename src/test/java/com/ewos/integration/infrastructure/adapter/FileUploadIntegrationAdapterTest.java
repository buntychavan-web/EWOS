package com.ewos.integration.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionContext;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUploadIntegrationAdapterTest {

    private final FileUploadIntegrationAdapter adapter =
            new FileUploadIntegrationAdapter(new BusinessErrorClassifier());

    @Test
    void typeIsFileUpload() {
        assertThat(adapter.type()).isEqualTo(IntegrationAdapterType.FILE_UPLOAD);
    }

    @Test
    void writesTheRawPayloadVerbatim(@TempDir Path dir) throws IOException {
        String configJson = "{\"outputDirectory\": \"" + dir.toString().replace("\\", "\\\\") + "\"}";
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "PAYROLL_RUN:abc",
                        "{\"x\":1}", configJson);

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.SUCCESS);
        Path file = dir.resolve("PAYROLL_RUN_abc.json");
        assertThat(Files.readString(file)).isEqualTo("{\"x\":1}");
    }

    @Test
    void defaultsToAnEmptyJsonObjectWhenPayloadIsNull(@TempDir Path dir) throws IOException {
        String configJson = "{\"outputDirectory\": \"" + dir.toString().replace("\\", "\\\\") + "\"}";
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", null, configJson);

        adapter.execute(ctx);

        Path file = dir.resolve("corr-1.json");
        assertThat(Files.readString(file)).isEqualTo("{}");
    }

    @Test
    void failsWithConfigurationClassificationWhenConfigJsonMissing() {
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", "{}", "");

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(result.errorClassification()).isEqualTo(ErrorClassification.CONFIGURATION);
    }
}
