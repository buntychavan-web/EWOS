package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollJournalStatus;
import com.ewos.payroll.domain.PayrollJournalType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PayrollJournalResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID payrollRunId,
        String journalNumber,
        LocalDate journalDate,
        PayrollJournalType journalType,
        PayrollJournalStatus status,
        String currency,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        Instant approvedAt,
        UUID approvedBy,
        Instant postedAt,
        UUID postedBy,
        Instant exportedAt,
        UUID exportedBy,
        String exportFormat,
        String exportReference,
        String notes,
        List<PayrollJournalLineResponse> lines,
        long versionNo) {}
