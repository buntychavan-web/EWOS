package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import java.math.BigDecimal;
import java.util.UUID;

public record PayslipLineResponse(
        UUID id,
        UUID payComponentId,
        String componentCode,
        String componentName,
        PayComponentKind kind,
        PayComponentCalculationType calculationType,
        BigDecimal amount,
        BigDecimal percentageApplied,
        int sortOrder) {}
