package com.ewos.reimbursement.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.reimbursement.domain.ReceiptOcrResult;
import org.junit.jupiter.api.Test;

class NoOpReceiptOcrServiceTest {

    private final NoOpReceiptOcrService service = new NoOpReceiptOcrService();

    @Test
    void supportsEveryMimeType() {
        assertThat(service.supports("image/jpeg")).isTrue();
        assertThat(service.supports("application/pdf")).isTrue();
        assertThat(service.supports("totally/unknown")).isTrue();
    }

    @Test
    void neverCallsAThirdPartyAndReturnsAnHonestEmptyResult() {
        ReceiptOcrResult result = service.extract("fake receipt bytes".getBytes(), "image/jpeg");

        assertThat(result).isEqualTo(ReceiptOcrResult.EMPTY);
        assertThat(result.merchantName()).isNull();
        assertThat(result.totalAmount()).isNull();
        assertThat(result.suggestedCategoryId()).isNull();
        assertThat(result.fieldConfidence()).isEmpty();
    }

    @Test
    void doesNotThrowForNullOrEmptyContent() {
        assertThat(service.extract(null, "image/jpeg")).isEqualTo(ReceiptOcrResult.EMPTY);
        assertThat(service.extract(new byte[0], "image/jpeg")).isEqualTo(ReceiptOcrResult.EMPTY);
    }

    @Test
    void reportsANonBlankProviderVersion() {
        assertThat(service.providerVersion()).isNotBlank();
    }
}
