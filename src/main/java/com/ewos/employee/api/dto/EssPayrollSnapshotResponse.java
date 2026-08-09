package com.ewos.employee.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 27C — ESS Dashboard payroll card (PRD §4.1). {@code ytdGross}/{@code ytdTaxDeducted} sum
 * every payslip whose {@code periodStart} falls in the current calendar year; {@code null} (never
 * zero) when the caller has no payslips at all, so the UI can distinguish "no data yet" from "zero
 * pay this year."
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EssPayrollSnapshotResponse(
        UUID latestPayslipId,
        LocalDate latestPayslipPeriodStart,
        LocalDate latestPayslipPeriodEnd,
        BigDecimal ytdGross,
        BigDecimal ytdTaxDeducted) {}
