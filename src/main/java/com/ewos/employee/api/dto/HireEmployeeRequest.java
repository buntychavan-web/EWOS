package com.ewos.employee.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record HireEmployeeRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        UUID personId,
        @NotBlank
                @Size(max = 64)
                @Pattern(
                        regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
                        message = "employee_number must be alphanumeric with . _ - allowed")
                String employeeNumber,
        @NotBlank @Size(max = 128) String firstName,
        @Size(max = 128) String middleName,
        @NotBlank @Size(max = 128) String lastName,
        @Size(max = 256) String displayName,
        @NotBlank @Email @Size(max = 320) String workEmail,
        @Email @Size(max = 320) String personalEmail,
        @Size(max = 32) String phone,
        LocalDate dateOfBirth,
        @Size(max = 32) String genderCode,
        UUID primaryOrgUnitId,
        UUID managerEmployeeId,
        UUID employmentTypeId,
        @NotNull LocalDate hireDate) {}
