package com.ewos.recruitment.domain;

import com.ewos.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Lifecycle guards for a {@link JobRequisition}. Rejects illegal state transitions. */
@Component
public class RequisitionPolicy {

    public void assertEditable(JobRequisition r) {
        if (r.getStatus() != RequisitionStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Requisition can only be edited in DRAFT state (current: "
                            + r.getStatus()
                            + ")");
        }
    }

    public void assertSubmittable(JobRequisition r) {
        if (r.getStatus() != RequisitionStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Requisition must be DRAFT to submit (current: " + r.getStatus() + ")");
        }
        if (r.getHeadcount() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Headcount must be at least 1");
        }
        if (r.getJustification() == null || r.getJustification().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Justification is required to submit");
        }
    }

    public void assertDecidable(JobRequisition r) {
        if (r.getStatus() != RequisitionStatus.PENDING_APPROVAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Requisition must be PENDING_APPROVAL to decide (current: "
                            + r.getStatus()
                            + ")");
        }
    }

    public void assertOpenable(JobRequisition r) {
        if (r.getStatus() != RequisitionStatus.APPROVED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Requisition must be APPROVED to open (current: " + r.getStatus() + ")");
        }
    }

    public void assertHoldable(JobRequisition r) {
        if (r.getStatus() != RequisitionStatus.OPEN) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Requisition must be OPEN to hold (current: " + r.getStatus() + ")");
        }
    }

    public void assertResumable(JobRequisition r) {
        if (r.getStatus() != RequisitionStatus.ON_HOLD) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Requisition must be ON_HOLD to resume (current: " + r.getStatus() + ")");
        }
    }

    public void assertFillable(JobRequisition r) {
        if (r.getStatus() != RequisitionStatus.OPEN && r.getStatus() != RequisitionStatus.ON_HOLD) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Requisition must be OPEN or ON_HOLD to record a fill (current: "
                            + r.getStatus()
                            + ")");
        }
        if (!r.hasVacancy()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Requisition has no remaining vacancy to fill");
        }
    }

    public void assertCloseable(JobRequisition r) {
        RequisitionStatus s = r.getStatus();
        if (s == RequisitionStatus.CLOSED
                || s == RequisitionStatus.CANCELLED
                || s == RequisitionStatus.REJECTED
                || s == RequisitionStatus.FILLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Requisition is already terminal (current: " + s + ")");
        }
    }

    public void assertCancellable(JobRequisition r) {
        RequisitionStatus s = r.getStatus();
        if (s == RequisitionStatus.FILLED
                || s == RequisitionStatus.CLOSED
                || s == RequisitionStatus.CANCELLED
                || s == RequisitionStatus.REJECTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Requisition is already terminal (current: " + s + ")");
        }
    }
}
