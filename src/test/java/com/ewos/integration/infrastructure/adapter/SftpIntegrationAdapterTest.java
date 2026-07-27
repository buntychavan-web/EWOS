package com.ewos.integration.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionContext;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SftpIntegrationAdapterTest {

    private final SftpTransport transport = mock(SftpTransport.class);
    private final SftpIntegrationAdapter adapter =
            new SftpIntegrationAdapter(transport, new BusinessErrorClassifier());

    @Test
    void typeIsSftp() {
        assertThat(adapter.type()).isEqualTo(IntegrationAdapterType.SFTP);
    }

    @Test
    void uploadsThePayloadViaTheConfiguredTransport() throws IOException {
        String configJson =
                "{\"host\": \"sftp.example.com\", \"port\": 2222, \"remotePath\": \"/inbound\","
                        + " \"username\": \"svc\", \"credentialRef\": \"vault:sftp\"}";
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "PAYROLL_RUN_EXPORT",
                        "PAYROLL_RUN:abc",
                        "{\"x\":1}",
                        configJson);

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.SUCCESS);
        verify(transport)
                .upload(
                        eq(
                                new SftpTransport.SftpTarget(
                                        "sftp.example.com", 2222, "svc", "vault:sftp", "/inbound")),
                        eq("PAYROLL_RUN_abc.json"),
                        any());
    }

    @Test
    void classifiesTransportFailuresViaTheErrorClassifier() throws IOException {
        String configJson = "{\"host\": \"sftp.example.com\", \"remotePath\": \"/inbound\"}";
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "PAYROLL_RUN_EXPORT",
                        "corr-1",
                        "{}",
                        configJson);
        doThrow(new IOException("connection refused")).when(transport).upload(any(), any(), any());

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(result.errorClassification()).isEqualTo(ErrorClassification.EXTERNAL_SYSTEM);
    }

    @Test
    void failsWithConfigurationClassificationWhenHostMissing() {
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "PAYROLL_RUN_EXPORT",
                        "corr-1",
                        "{}",
                        "{\"remotePath\": \"/inbound\"}");

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(result.errorClassification()).isEqualTo(ErrorClassification.CONFIGURATION);
    }
}
