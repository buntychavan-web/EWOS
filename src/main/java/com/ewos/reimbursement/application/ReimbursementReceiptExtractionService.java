package com.ewos.reimbursement.application;

import com.ewos.reimbursement.api.dto.ReceiptExtractionRequest;
import com.ewos.reimbursement.api.dto.ReceiptExtractionResponse;
import com.ewos.reimbursement.domain.ReceiptOcrResult;
import com.ewos.reimbursement.domain.ReceiptOcrService;
import com.ewos.shared.exception.ApiException;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 2 OCR foundation. Purely a read-side helper — never touches {@code ReimbursementClaim} or
 * any other persisted state. {@code content} arrives base64-encoded inside the request itself (see
 * {@link ReceiptExtractionRequest}), never fetched from a {@code storageUri}. The default {@code
 * NoOpReceiptOcrService} binding means this always succeeds with an honestly-empty result today —
 * see the Sprint 2 OCR architecture addendum for why no real provider is wired in this sprint.
 */
@Service
@Transactional(readOnly = true)
public class ReimbursementReceiptExtractionService {

    private final ReceiptOcrService ocr;

    public ReimbursementReceiptExtractionService(ReceiptOcrService ocr) {
        this.ocr = ocr;
    }

    public ReceiptExtractionResponse extract(ReceiptExtractionRequest request) {
        if (!ocr.supports(request.mimeType())) {
            return emptyResponse();
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(request.contentBase64());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "contentBase64 is not valid base64", e);
        }
        ReceiptOcrResult result = ocr.extract(content, request.mimeType());
        return new ReceiptExtractionResponse(
                result.merchantName(),
                result.billDate(),
                result.totalAmount(),
                result.currency(),
                result.suggestedCategoryId(),
                result.fieldConfidence(),
                ocr.providerVersion());
    }

    private ReceiptExtractionResponse emptyResponse() {
        return new ReceiptExtractionResponse(
                null, null, null, null, null, java.util.Map.of(), ocr.providerVersion());
    }
}
