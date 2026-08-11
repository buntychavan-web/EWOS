package com.ewos.reimbursement.domain;

/**
 * How a claim's values were originally entered. Purely descriptive/reporting — {@code OCR_ASSISTED}
 * does NOT mean any field's value is OCR-authoritative; every value in {@link ReimbursementClaim}
 * only ever reaches the database via the employee's own confirmed create/update request, whether or
 * not an OCR suggestion informed what they typed.
 */
public enum ReimbursementDataSource {
    MANUAL,
    OCR_ASSISTED
}
