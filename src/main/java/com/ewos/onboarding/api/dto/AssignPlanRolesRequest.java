package com.ewos.onboarding.api.dto;

import java.util.UUID;

public record AssignPlanRolesRequest(UUID managerEmployeeId, UUID buddyEmployeeId) {}
