package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.GLAccountType;
import com.ewos.payroll.domain.PayrollJournalLineSourceKind;
import java.math.BigDecimal;
import java.util.UUID;

public record PayrollJournalLineResponse(
        UUID id,
        int lineNo,
        String glAccountCode,
        String glAccountName,
        GLAccountType accountType,
        String costCentreCode,
        String businessUnitCode,
        String departmentCode,
        PayrollJournalLineSourceKind sourceKind,
        String sourceReference,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String currency,
        String description) {}
