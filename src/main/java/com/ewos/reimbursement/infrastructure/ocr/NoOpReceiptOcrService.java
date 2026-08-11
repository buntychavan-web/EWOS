package com.ewos.reimbursement.infrastructure.ocr;

import com.ewos.reimbursement.domain.ReceiptOcrResult;
import com.ewos.reimbursement.domain.ReceiptOcrService;
import org.springframework.stereotype.Component;

/**
 * Default receipt-OCR binding. Ships in-tree so the reimbursement extraction endpoint is functional
 * out of the box — no third-party call, no API key, no cost, always returns {@link
 * ReceiptOcrResult#EMPTY} honestly rather than fabricating a result. Mirrors {@code
 * com.ewos.ats.infrastructure.parsing.NoOpResumeParser} exactly. Deployments that want real
 * extraction override this with a {@code @Primary} bean.
 */
@Component
public class NoOpReceiptOcrService implements ReceiptOcrService {

    private static final String VERSION = "noop-1.0";

    @Override
    public boolean supports(String mimeType) {
        return true;
    }

    @Override
    public ReceiptOcrResult extract(byte[] content, String mimeType) {
        return ReceiptOcrResult.EMPTY;
    }

    @Override
    public String providerVersion() {
        return VERSION;
    }
}
