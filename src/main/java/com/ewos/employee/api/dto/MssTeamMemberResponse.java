package com.ewos.employee.api.dto;

import com.ewos.employee.domain.EmployeeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 27C — one direct report's My Team list row (PRD §4.3). Every field except {@code
 * employeeId} is subject to {@code MssFieldVisibilityService.canManagerView} masking: a masked
 * field is {@code null} on the wire and its name appears in {@code maskedFields}, so the caller can
 * distinguish "no data" from "not permitted to see this."
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MssTeamMemberResponse(
        UUID employeeId,
        String displayName,
        String employeeNumber,
        String workEmail,
        String phone,
        LocalDate hireDate,
        EmployeeStatus status,
        MssOrgUnitRef primaryOrgUnit,
        String managerName,
        String employmentType,
        List<String> maskedFields) {}
