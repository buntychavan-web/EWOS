package com.ewos.onboarding.api.dto;

import java.util.UUID;

/** {@code null} clears the assignment (task reverts to unassigned/owner-only). */
public record ReassignOnboardingTaskRequest(UUID assignedEmployeeId) {}
