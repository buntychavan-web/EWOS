package com.ewos.employee.api.dto;

import com.ewos.employee.domain.EmployeeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 27C — single direct report's My Team drill-down (PRD §4.4). Adds the fields a list row
 * omits ({@code dateOfBirth}, {@code personalEmail}, {@code emergencyContactName}, {@code
 * emergencyContactPhone}); these are not seeded in V77 so they stay default-masked until a tenant
 * admin opts in via {@code MssFieldVisibilityService.setVisibility}. Same masking contract as
 * {@link MssTeamMemberResponse}: a masked field is {@code null} and its name is in {@code
 * maskedFields}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MssTeamMemberDetailResponse(
        UUID employeeId,
        String displayName,
        String employeeNumber,
        String workEmail,
        String phone,
        LocalDate dateOfBirth,
        LocalDate hireDate,
        EmployeeStatus status,
        MssOrgUnitRef primaryOrgUnit,
        String managerName,
        String employmentType,
        String personalEmail,
        String emergencyContactName,
        String emergencyContactPhone,
        List<String> maskedFields) {}
