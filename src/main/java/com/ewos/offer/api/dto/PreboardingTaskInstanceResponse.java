package com.ewos.offer.api.dto;

import com.ewos.offer.domain.preboarding.PreboardingTaskOwner;
import com.ewos.offer.domain.preboarding.PreboardingTaskStatus;
import com.ewos.offer.domain.preboarding.PreboardingTaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PreboardingTaskInstanceResponse(
        UUID id,
        UUID checklistId,
        UUID templateId,
        String name,
        PreboardingTaskType taskType,
        PreboardingTaskOwner owner,
        UUID assignedEmployeeId,
        boolean mandatory,
        int sortOrder,
        PreboardingTaskStatus status,
        LocalDate dueDate,
        Instant startedAt,
        Instant completedAt,
        UUID completedBy,
        String externalRef,
        String resultJson,
        String notes,
        long versionNo) {}
