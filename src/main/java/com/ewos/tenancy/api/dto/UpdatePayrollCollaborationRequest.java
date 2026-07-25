package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.PayrollCollaborationScope;
import com.ewos.tenancy.domain.PayrollCollaborationStatus;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record UpdatePayrollCollaborationRequest(
        PayrollCollaborationScope scope,
        PayrollCollaborationStatus status,
        LocalDate effectiveTo,
        @Positive Integer slaDays) {}
