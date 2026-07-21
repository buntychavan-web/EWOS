package com.ewos.onboarding.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Converts a candidate whose offer was accepted into a real {@link
 * com.ewos.employee.domain.Employee} record. Supplied when the pre-boarding checklist is confirmed
 * as JOINED. If {@code employeeNumber} is blank the {@link
 * com.ewos.offer.domain.EmployeeIdGenerator} default binding produces one.
 */
public record ConvertCandidateRequest(
        @NotNull UUID offerId,
        @Size(max = 64) String employeeNumber,
        @Size(max = 320) String workEmail,
        LocalDate joiningDate,
        UUID primaryOrgUnitId,
        UUID managerEmployeeId) {}
