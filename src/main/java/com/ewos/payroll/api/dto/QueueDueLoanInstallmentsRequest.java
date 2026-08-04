package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record QueueDueLoanInstallmentsRequest(
        @NotNull UUID tenantId, @NotNull UUID companyId, @NotEmpty List<UUID> employeeIds) {}
