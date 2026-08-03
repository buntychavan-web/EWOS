package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.PayrollInsightResponse;
import com.ewos.payroll.domain.Payslip;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 24K item 6 — AI-Ready Payroll Foundation, architecture only. No LLM is integrated anywhere
 * in this codebase; this interface exists so that when one eventually is, every caller (ESS
 * "explain my payslip," an admin assistant, personalized tax-saving suggestions) already talks to a
 * stable {@link PayrollInsightResponse} shape instead of the raw domain model. {@link
 * RuleBasedPayrollInsightProvider} is the only implementation today — it is 100% deterministic,
 * rule-based, and reuses existing calculation/audit data (never inventing a fact an LLM would
 * otherwise have to hallucinate). A future LLM-backed implementation would sit behind this same
 * interface, most naturally by taking a {@code RuleBasedPayrollInsightProvider}'s output as
 * grounding context rather than replacing it — this codebase's calculations must always remain the
 * source of truth, never the model.
 */
public interface PayrollInsightProvider {

    /** Explains every fact this codebase can already compute about one payslip. */
    List<PayrollInsightResponse> explainPayslip(Payslip payslip);

    /**
     * Anomalies worth surfacing for one payroll run — currently backed by {@link
     * PayrollExceptionReportService}, exposed in the same {@link PayrollInsightResponse} shape so
     * an admin assistant can present exceptions and payslip explanations side by side.
     */
    List<PayrollInsightResponse> explainRunExceptions(UUID tenantId, UUID runId);
}
