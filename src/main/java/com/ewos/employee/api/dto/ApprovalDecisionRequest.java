package com.ewos.employee.api.dto;

import jakarta.validation.constraints.Size;

/** Sprint 27B — body of an individual approve/reject act-through call. */
public record ApprovalDecisionRequest(@Size(max = 2048) String notes) {}
