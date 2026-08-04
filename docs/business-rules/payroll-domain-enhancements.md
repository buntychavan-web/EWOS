# Payroll domain enhancements — statutory verification log

Sprint 24K (Payroll Version 1 Freeze Sprint) implemented three mandatory domain enhancements:
LTA block management (§8.1), prorated monthly tax recovery (§8.2), and tax on variable payments
(§8.3). Per the sprint's explicit instruction, every rule was implemented only where the
underlying Income Tax Act provision or payroll practice is stable and long-established; anywhere a
rule depends on a figure that can change with a Finance Act amendment, CBDT notification, or EPFO
circular, the engine does not hardcode a guess — it stores the figure as configuration and flags it
here for confirmation before the tenant relies on it in production.

This file is the single place engineering and payroll-compliance should check before go-live for
"is this number actually correct for us."

## §8.1 — LTA block boundary (`lta_block_configurations`)

**What is hardcoded (safe, stable law):** the block *mechanism* — Section 10(5) read with Rule 2B
exempts LTA for up to 2 journeys performed in a government-fixed 4-calendar-year block, with one
unused journey from a block eligible to carry forward into the first calendar year of the next
block. This structure has been stable for decades and is implemented directly in
`LtaBlockConfiguration`/`LtaBlockService`.

**What is NOT hardcoded (needs confirmation):** the *current* block's boundary years. The seed row
migrated in `V55__tax_domain_enhancements.sql` uses `anchorBlockStartYear = 2022`,
`blockDurationYears = 4` (i.e. a commonly-cited 2022–2025 block, rolling automatically into
2026–2029 thereafter) as a reasonable default so the engine is usable out of the box — **not**
because that boundary has been verified against the specific government notification in effect for
this deployment.

**Action required before production reliance:** confirm the exact current block boundary against
the applicable CBDT/government notification, then update (or add a company-specific override to)
the `lta_block_configurations` row via `POST /api/v1/payroll/lta/configurations` — no code change
needed either way, that is the entire point of making it configuration.

**Also not implemented:** any mode-of-travel fare cap (air/rail/road sub-limits). The exemption
gate implemented is the journey-count quota, not a monetary cap — this is the well-established,
stable rule; mode-specific fare ceilings are a separate, more granular rule that was intentionally
left out rather than guessed at.

## §8.2 — Prorated monthly tax recovery

Implemented directly, no configuration needed: the even-share monthly recovery
`(annualLiability − ytdTdsDeducted) / monthsRemaining` is prorated down when
`payableEarningsThisPeriod < monthlyRecurringTaxableSalary` (new joiner, LOP, salary hold), instead
of deducting the full normal amount against a shrunken payslip. This is arithmetic, not a statutory
figure, so there is nothing here to flag for verification.

## §8.3 — Tax on variable payments

Also implemented directly, no configuration needed: a one-time payment (bonus, incentive,
ex-gratia, one-off arrears) is never annualised; only the incremental tax it causes
(`computeAnnualTax(recurring + oneTime) − recurringAnnualTaxLiability`) is recovered, in full, the
same period. Slab rates, rebate thresholds, and surcharge slabs it feeds through are the existing
`IncomeTaxSlab`/`IncomeTaxPolicy`/`IncomeTaxSurchargeSlab` configuration tables seeded in earlier
sprints — already reviewed for statutory accuracy, not new to this sprint.

## How to update a flagged assumption

1. Confirm the correct value against the primary source (Finance Act, CBDT notification, EPFO
   circular, etc.) — never against a secondary summary.
2. Update the relevant configuration row through its API (never a direct SQL edit against
   production) so the change is audited like any other admin action.
3. Record the confirmation (source, date, reviewer) in this file's history below.

## Verification history

| Date | Item | Confirmed value | Source | Confirmed by |
|------|------|------------------|--------|---------------|
| _(none yet)_ | LTA block boundary | — | — | — |
