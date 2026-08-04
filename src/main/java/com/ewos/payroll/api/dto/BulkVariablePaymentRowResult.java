package com.ewos.payroll.api.dto;

import java.util.List;
import java.util.UUID;

public record BulkVariablePaymentRowResult(
        int rowNumber,
        String employeeNumber,
        boolean valid,
        List<String> errors,
        UUID createdArrearId) {}
