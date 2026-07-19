package com.ewos.employee.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateEmployeeRequest(
        @Size(max = 128) String firstName,
        @Size(max = 128) String middleName,
        @Size(max = 128) String lastName,
        @Size(max = 256) String displayName,
        @Email @Size(max = 320) String workEmail,
        @Email @Size(max = 320) String personalEmail,
        @Size(max = 32) String phone,
        LocalDate dateOfBirth,
        @Size(max = 32) String genderCode,
        UUID primaryOrgUnitId,
        UUID managerEmployeeId,
        UUID employmentTypeId) {}
