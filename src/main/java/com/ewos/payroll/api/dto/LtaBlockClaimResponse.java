package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.LtaClaimType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LtaBlockClaimResponse(
        UUID id,
        UUID employeeId,
        int blockStartYear,
        int blockEndYear,
        LtaClaimType claimType,
        String fiscalYear,
        LocalDate claimDate,
        BigDecimal ltaCreditedAmount,
        BigDecimal amountClaimed,
        BigDecimal taxFreeAmount,
        BigDecimal taxableAmount,
        boolean carriedForwardFromPreviousBlock,
        String notes) {}
