# Payroll module

**Package:** `com.ewos.payroll` | **Depends on:** `shared`, `identity`, `employee`, `organization`, `leave`
**Introduced:** WP-009 (base) | **Finalised:** M1–M7 (Business Bible Volume 4)
**Release:** EWOS HCM — Enterprise Payroll Version 1.0 (tag `v1.0-payroll`, main commit `fce781e`)
**Status:** FROZEN — see [Freeze policy](#freeze-policy) below.

## Freeze policy
The Payroll module is functionally complete per Volume 4 of the EWOS Business Bible and is frozen
at v1.0. No further Payroll features will ship unless one of the following applies:

1. **Critical defect** — a bug that prevents payroll from running correctly on production.
2. **Production issue** — an operational incident requiring code changes.
3. **Business Bible enhancement** — a formally documented revision to Volume 4.
4. **Approved customer change request** — a signed-off CR referencing this freeze.

Bug-fix branches follow the `claude/payroll-fix-<slug>` convention and require a linked issue.
Any non-freeze-conforming Payroll PR is expected to be rejected on review.

## Purpose
End-to-end enterprise payroll — configuration, processing, off-cycle runs, final settlement,
bank remittance, statutory compliance, general-ledger accounting, and reports/dashboards.

## Milestones

| Milestone | Migration | Scope |
|---|---|---|
| WP-009 (base) | V14 | Pay components, periods, compensation, runs, payslips |
| M1 Configuration | V15 | Pay groups, statutory settings, employee bank accounts, payroll profiles |
| M2 Processing | V16 | LOP integration, arrears, validation, freeze |
| M3 Supplementary / F&F | V17 | Off-cycle runs, Full & Final settlement |
| M4 Bank Processing | V18 | Bank advice, payment instructions, CSV export |
| M5 Statutory Compliance | V19 | Statutory deductions, monthly challan roll-up |
| M6 Accounting | V20 (part) | GL config, journals, ERP export, reconciliation |
| M7 Reports | V20 (part) | Registers, variance, dashboards, scheduled reports |

## Domain (post-finalisation)

### Configuration
`PayComponent`, `PayrollPeriod`, `PayGroup`, `StatutorySetting`, `EmployeeCompensation`,
`EmployeeBankAccount`, `EmployeePayrollProfile`, `CostCentre`, `BusinessUnit`,
`EmployeeCostAllocation`, `GLAccount`, `GLMapping`.

### Processing
`PayrollRun` (types: REGULAR / SUPPLEMENTARY / FINAL_SETTLEMENT), `Payslip`, `PayslipLine`,
`PayrollArrear`, `FinalSettlement`, `PayrollCalculator`, `LopCalculator`, `PayrollPolicy`,
`PayrollValidator`.

### Remittance & compliance
`BankAdvice`, `PaymentInstruction`, `StatutoryDeduction`, `StatutoryChallan`,
`StatutoryClassifier`, `BankAdviceCsvExporter`.

### Accounting
`PayrollJournal`, `PayrollJournalLine`, `PayrollJournalGenerator`, `PayrollJournalCsvExporter`.

### Reports
`RegisterCsvExporter`, `ScheduledReport`.

## Permissions
`PAYROLL_READ`, `PAYROLL_WRITE`, `PAYROLL_RUN`, `PAYROLL_ADMIN`, `PAYROLL_CONFIG`,
`PAYROLL_ACCOUNTING`, `PAYROLL_REPORTS`. All seeded to `SYSTEM_ADMIN`.

## API bases (all require `X-Tenant-Id`)

| Base | Purpose |
|---|---|
| `/api/v1/payroll/components` | Pay component catalogue (cached) |
| `/api/v1/payroll/periods` | Period lifecycle |
| `/api/v1/payroll/paygroups` | Pay-group config |
| `/api/v1/payroll/statutory-settings` | Compliance rates |
| `/api/v1/payroll/bank-accounts` | Employee bank accounts |
| `/api/v1/payroll/profiles` | Employee payroll profiles |
| `/api/v1/payroll/compensations` | Effective-dated salary structure |
| `/api/v1/payroll/runs` | Payroll runs (regular + supplementary + finalize + freeze) |
| `/api/v1/payroll/payslips` | Immutable payslip snapshots |
| `/api/v1/payroll/arrears` | Retro adjustments |
| `/api/v1/payroll/settlements` | Full & Final settlements |
| `/api/v1/payroll/validate` | Pre-flight validator |
| `/api/v1/payroll/bank-advices` | Bank remittance + payment instructions + CSV |
| `/api/v1/payroll/deductions` | Statutory deduction snapshots |
| `/api/v1/payroll/challans` | Statutory monthly challans |
| `/api/v1/payroll/gl` | Cost centres, business units, GL accounts, mappings, allocations |
| `/api/v1/payroll/journals` | Journal generate/approve/post/export/reconcile |
| `/api/v1/payroll/reports` | Registers, variance, cost-centre, dashboards (JSON + CSV) |
| `/api/v1/payroll/scheduled-reports` | Cron-driven recurring reports |

## Events — topic `ewos.payroll.event`
See `PayrollEventType`. All events published on `AFTER_COMMIT`; kafka-optional.

## ERP integration
The generic CSV writer ships in-tree. SAP IDoc, Oracle EBS staging, and Microsoft Dynamics D365
generic-journal writers plug in behind the same `PayrollJournalExporter` contract — implemented
per tenant/site because the target instance schema and transport differ. `POST /journals/{id}/record-export`
records the format + reference so downstream reconciliation can join.
