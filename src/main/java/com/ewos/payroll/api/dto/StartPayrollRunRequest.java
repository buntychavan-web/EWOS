package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartPayrollRunRequest(
        @NotNull UUID tenantId, @NotNull UUID companyId, @NotNull UUID payrollPeriodId) {}
