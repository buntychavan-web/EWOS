package com.ewos.payroll.api.dto;

import java.util.UUID;

public record ValidationIssueResponse(
        UUID employeeId, String employeeName, String code, String message) {}
