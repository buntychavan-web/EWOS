package com.ewos.integration.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.ewos.integration.domain.BusinessErrorClassifier;
import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionContext;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class RestIntegrationAdapterTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    private final RestIntegrationAdapter adapter =
            new RestIntegrationAdapter(restTemplate, new BusinessErrorClassifier());

    @Test
    void typeIsRest() {
        assertThat(adapter.type()).isEqualTo(IntegrationAdapterType.REST);
    }

    @Test
    void postsThePayloadAndReturnsSuccessOn2xx() {
        server.expect(requestTo("https://partner.example.com/hook"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", "{\"x\":1}",
                        "{\"url\": \"https://partner.example.com/hook\"}");

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.SUCCESS);
        server.verify();
    }

    @Test
    void usesTheConfiguredHttpMethod() {
        server.expect(requestTo("https://partner.example.com/hook"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", "{}",
                        "{\"url\": \"https://partner.example.com/hook\", \"method\": \"PUT\"}");

        adapter.execute(ctx);

        server.verify();
    }

    @Test
    void classifiesNon2xxResponsesAsExternalSystem() {
        server.expect(requestTo("https://partner.example.com/hook"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", "{}",
                        "{\"url\": \"https://partner.example.com/hook\"}");

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(result.errorClassification()).isEqualTo(ErrorClassification.EXTERNAL_SYSTEM);
    }

    @Test
    void classifiesServerErrorsViaTheErrorClassifier() {
        server.expect(requestTo("https://partner.example.com/hook")).andRespond(withServerError());
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", "{}",
                        "{\"url\": \"https://partner.example.com/hook\"}");

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(result.errorClassification()).isEqualTo(ErrorClassification.EXTERNAL_SYSTEM);
    }

    @Test
    void failsWithConfigurationClassificationWhenUrlMissing() {
        IntegrationExecutionContext ctx =
                new IntegrationExecutionContext(
                        UUID.randomUUID(), UUID.randomUUID(), "PAYROLL_RUN_EXPORT", "corr-1", "{}", "{}");

        IntegrationAdapterResult result = adapter.execute(ctx);

        assertThat(result.outcome()).isEqualTo(IntegrationExecutionOutcome.FAILURE);
        assertThat(result.errorClassification()).isEqualTo(ErrorClassification.CONFIGURATION);
    }
}
