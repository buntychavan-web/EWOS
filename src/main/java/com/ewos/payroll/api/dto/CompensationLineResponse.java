package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CompensationLineResponse(
        UUID id,
        UUID payComponentId,
        String payComponentCode,
        BigDecimal amount,
        BigDecimal percentage) {}
