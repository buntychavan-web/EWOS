package com.ewos.reimbursement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ewos.reimbursement.api.dto.ReceiptExtractionRequest;
import com.ewos.reimbursement.api.dto.ReceiptExtractionResponse;
import com.ewos.reimbursement.domain.ReceiptOcrResult;
import com.ewos.reimbursement.domain.ReceiptOcrService;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sprint 2 OCR foundation. This service never writes claim data and never fetches a storageUri — it
 * is a pure, transient pass-through to whatever {@link ReceiptOcrService} binding is wired, which
 * in Sprint 2 is always {@code NoOpReceiptOcrService}.
 */
@ExtendWith(MockitoExtension.class)
class ReimbursementReceiptExtractionServiceTest {

    @Mock ReceiptOcrService ocr;

    private ReimbursementReceiptExtractionService service;

    @BeforeEach
    void setUp() {
        service = new ReimbursementReceiptExtractionService(ocr);
    }

    @Test
    void decodesBase64ContentAndDelegatesToTheProvider() {
        when(ocr.supports("image/jpeg")).thenReturn(true);
        when(ocr.extract("hello".getBytes(), "image/jpeg"))
                .thenReturn(
                        new ReceiptOcrResult(
                                "Cafe Coffee Day",
                                LocalDate.of(2026, 1, 15),
                                new BigDecimal("450.00"),
                                "INR",
                                UUID.randomUUID(),
                                Map.of("totalAmount", 0.95),
                                "{}"));
        when(ocr.providerVersion()).thenReturn("noop-1.0");

        String base64 = Base64.getEncoder().encodeToString("hello".getBytes());
        ReceiptExtractionResponse response =
                service.extract(new ReceiptExtractionRequest("image/jpeg", base64));

        assertThat(response.merchantName()).isEqualTo("Cafe Coffee Day");
        assertThat(response.totalAmount()).isEqualByComparingTo("450.00");
        assertThat(response.fieldConfidence()).containsEntry("totalAmount", 0.95);
    }

    @Test
    void unsupportedMimeTypeReturnsEmptyWithoutCallingExtract() {
        when(ocr.supports("application/zip")).thenReturn(false);
        when(ocr.providerVersion()).thenReturn("noop-1.0");

        String base64 = Base64.getEncoder().encodeToString("x".getBytes());
        ReceiptExtractionResponse response =
                service.extract(new ReceiptExtractionRequest("application/zip", base64));

        assertThat(response.merchantName()).isNull();
        assertThat(response.totalAmount()).isNull();
    }

    @Test
    void invalidBase64Rejected() {
        when(ocr.supports("image/jpeg")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.extract(
                                        new ReceiptExtractionRequest(
                                                "image/jpeg", "not valid base64!!!")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void requestHasNoStorageUriField() {
        // Structural guarantee, not just a runtime check: ReceiptExtractionRequest carries only
        // mimeType and base64 content — there is nothing to dereference as a URI, so this service
        // has no way to fetch a storageUri even if it wanted to.
        assertThat(ReceiptExtractionRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("mimeType", "contentBase64");
    }
}
