package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalReconciliationResponse(
        UUID journalId,
        UUID payrollRunId,
        BigDecimal runGross,
        BigDecimal runDeductions,
        BigDecimal runNet,
        BigDecimal journalTotalDebit,
        BigDecimal journalTotalCredit,
        boolean balanced,
        BigDecimal debitVsCreditDelta,
        BigDecimal expenseVsRunGrossDelta) {}
