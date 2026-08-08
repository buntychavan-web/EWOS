package com.ewos.employee.api.dto;

import com.ewos.employee.domain.ApprovalAction;
import com.ewos.employee.domain.ApprovalSourceModule;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Sprint 27B — one line of a bulk approve/reject request. */
public record BulkApprovalItemRequest(
        @NotNull ApprovalSourceModule sourceModule,
        @NotNull UUID sourceId,
        @NotNull ApprovalAction action,
        @Size(max = 2048) String notes) {}
