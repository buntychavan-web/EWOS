package com.ewos.employee.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TerminateEmployeeRequest(
        @NotNull LocalDate terminationDate, @Size(max = 256) String reason) {}
