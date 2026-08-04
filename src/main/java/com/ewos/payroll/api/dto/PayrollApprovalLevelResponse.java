package com.ewos.payroll.api.dto;

import java.util.UUID;

public record PayrollApprovalLevelResponse(
        UUID id, int levelNumber, String approverRoleCode, String description) {}
