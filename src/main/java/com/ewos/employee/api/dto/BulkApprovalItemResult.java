package com.ewos.employee.api.dto;

import com.ewos.employee.domain.ApprovalSourceModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Sprint 27B — outcome of one {@link BulkApprovalItemRequest} line. {@code error} is populated only
 * when {@code status} is {@code "FAILED"}; the message is deliberately generic where the failure
 * reason could otherwise be used to enumerate another employee's data (PRD §17).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BulkApprovalItemResult(
        ApprovalSourceModule sourceModule, UUID sourceId, String status, String error) {

    public static BulkApprovalItemResult success(ApprovalSourceModule module, UUID id) {
        return new BulkApprovalItemResult(module, id, "SUCCESS", null);
    }

    public static BulkApprovalItemResult failed(
            ApprovalSourceModule module, UUID id, String error) {
        return new BulkApprovalItemResult(module, id, "FAILED", error);
    }
}
