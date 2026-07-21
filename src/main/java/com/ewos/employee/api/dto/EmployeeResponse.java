package com.ewos.employee.api.dto;

import com.ewos.employee.domain.EmployeeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmployeeResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID personId,
        String employeeNumber,
        String firstName,
        String middleName,
        String lastName,
        String displayName,
        String workEmail,
        String personalEmail,
        String phone,
        LocalDate dateOfBirth,
        String genderCode,
        UUID primaryOrgUnitId,
        String primaryOrgUnitCode,
        UUID managerEmployeeId,
        UUID employmentTypeId,
        String employmentTypeCode,
        LocalDate hireDate,
        LocalDate terminationDate,
        EmployeeStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
