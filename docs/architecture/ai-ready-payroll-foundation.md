# AI-Ready Payroll Foundation (Sprint 24K item 6)

Per the sprint instruction: **no LLM is integrated anywhere in this codebase.** This document and
the code it describes are architecture only — the extension points a future AI feature would use,
built entirely with deterministic, rule-based logic today.

## What exists today

### `PayrollInsightProvider` / `PayrollInsightResponse`

`src/main/java/com/ewos/payroll/application/PayrollInsightProvider.java` is the extension point.
`RuleBasedPayrollInsightProvider` is the only implementation and the default Spring bean — it
answers two questions, both from data this codebase already computed and persisted, never invented:

- `explainPayslip(Payslip)` — one `PayrollInsightResponse` per payslip line (via the existing
  `PayslipLineExplainer`, built in Sprint 24J) plus one per matching-month `TdsAdjustmentLog` row
  (the §8.2/§8.3 audit trail), explaining *why* a deduction or an extra tax recovery happened this
  period.
- `explainRunExceptions(tenantId, runId)` — wraps `PayrollExceptionReportService`'s findings in the
  same `PayrollInsightResponse` shape, so exceptions and payslip explanations can be presented side
  by side by any future admin assistant UI.

Exposed today via `GET /api/v1/payroll/self-service/payslips/{id}/insights` (ESS — "explain my
payslip").

### Why this shape enables an LLM later without replacing it

A future LLM-backed implementation of `PayrollInsightProvider` (or a layer that wraps the
rule-based one) would take `RuleBasedPayrollInsightProvider`'s output as grounding context and
phrase it more naturally, rather than the model computing or guessing payroll facts itself. This
codebase's calculation services (`IncomeTaxCalculationService`, `PfCalculationService`, etc.) must
always remain the source of truth for what happened; an AI layer's only job would be explaining it
better, never deciding it. This is the same pattern already used for `PayslipSignatureService` (a
no-op today, a real certificate-backed signer later, same interface) — swap the implementation
bean, change nothing about the callers.

## What is explicitly NOT built (future work, per the sprint's own three named capabilities)

- **Personalized tax-saving suggestions.** Would need to compare an employee's current declarations
  (`EmployeeTaxDeclaration`) against Chapter VI-A limits and flag unused headroom. Not built this
  sprint — it is a genuinely new feature (suggestion generation + a UI to act on it), not an
  extension of `PayrollInsightProvider`'s "explain what already happened" scope.
- **Anomaly detection beyond `PayrollExceptionReportService`'s rules.** The exception report's
  thresholds (§ `PayrollExceptionReportService`) are fixed heuristics. A learned/statistical anomaly
  model (e.g. flagging a salary change that's unusual for *that specific employee's* history, not
  just a fixed percentage) is future work.
- **Admin AI assistant.** No conversational interface exists. `PayrollInsightProvider` is the data
  layer such an assistant would call; the assistant itself — orchestration, conversation state, an
  actual model — is not part of this sprint's scope.

## Why this shape

Building a real LLM integration without a decided model/vendor, cost budget, data-residency
review, and prompt-injection/safety review would be premature and risky — exactly what "architecture,
not integration" is guarding against. What's delivered instead is a stable seam
(`PayrollInsightProvider`) that already provides real value stand-alone (deterministic, auditable
explanations) and will not need to change shape when a model is eventually plugged in behind it.
