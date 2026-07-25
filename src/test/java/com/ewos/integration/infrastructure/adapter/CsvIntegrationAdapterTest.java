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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvIntegrationAdapterTest {

    private final CsvIntegrationAdapter adapter = new CsvIntegrationAdapter(new BusinessErrorClassifier());

    @Test
    void typeIsCsv() {
        org.assertj.core.api.Assertions.assertThat(adapter.type()).isEqualTo(IntegrationAdapterType.CSV);
    }

    @Test
    void writesACsvFileToTheConfiguredDirectory(@TempDir Path dir) throws IOException {
        String configJson = "{\"outputDirectory\": \"" + dir.toString().replace("\\", "\\\\") + "\"}";
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "PAYROLL_RUN:abc",
                        "{\"x\":1}", configJson);

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.SUCCESS);
        Path file = dir.resolve("PAYROLL_RUN_abc.csv");
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readString(file)).contains("PAYROLL_RUN:abc").contains("{\"\"x\"\":1}");
    }

    @Test
    void failsWithConfigurationClassificationWhenOutputDirectoryMissing() {
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", "{}", "{}");

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(result.errorClassification()).isEqualTo(ErrorClassification.CONFIGURATION);
    }

    @Test
    void failsWithConfigurationClassificationWhenConfigJsonBlank() {
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", "{}", null);

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(result.errorClassification()).isEqualTo(ErrorClassification.CONFIGURATION);
    }
}
