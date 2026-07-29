# Sprint 16 — Payroll Certification

**Scope:** Certify the calculation logic behind every payroll scenario named in the Sprint 16
brief against the automated test suite. This sandbox has no Docker/Postgres, so a live end-to-end
payroll run cannot be executed here; certification is therefore evidence-based — each scenario is
mapped to the unit/domain test(s) that assert its expected-vs-actual arithmetic, and every cited
test was re-run fresh as part of this sprint (see result column). Full end-to-end runs against a
real database happen in CI, which does have Docker (see GitHub Verification section of the main
report for the green run backing this).

| # | Scenario | Test evidence | Expected vs. actual | Result |
|---|----------|---------------|----------------------|--------|
| 1 | Monthly Payroll | `PayrollRunServiceTest.startProcessesOnceAccessIsGranted`, `PayrollCalculatorTest.mixedEarningsAndDeductions`, `PayslipServiceTest` | Regular run over a locked period generates one payslip per employee; gross = sum of earnings, net = gross − deductions, floored at zero (`netFloorsAtZeroWhenDeductionsExceedGross`) | PASS |
| 2 | New Joiner | `CandidateConversionServiceTest.convertFallsBackToTheOffersTargetJoiningDateWhenRequestOmitsIt`, `convertHandsOffToOnboardingIdempotentlyAndReturnsThePlanId` (Sprint 16), `LopCalculatorTest.effectiveBasicScalesByWorkedRatio` | Joining date resolves from request or offer; mid-period joining prorates basic by worked-day ratio | PASS |
| 3 | Exit | `ExitServiceTest`, `ResignationLifecyclePolicyTest` | Resignation → clearance → exit lifecycle transitions enforced; illegal transitions rejected | PASS |
| 4 | Full & Final Settlement | `FinalSettlementServiceTest.approveQueuesOnlyThePositiveAmountComponentsAsArrears`, `approveQueuesDeductionArrearsWithDeductionKind`, `settleStartsAFinalSettlementRunAndLocksTheRecord`, `FinalSettlementLifecycleTest` | Settlement aggregates leave encashment + gratuity + notice pay recovery/receivable + other earnings/deductions; approval queues each non-zero amount as an arrear of the correct kind; settle starts a dedicated FINAL_SETTLEMENT run and locks the record | PASS |
| 5 | Arrears | `PayrollArrearServiceTest.createAcceptsADeductionArrearAsWellAsAnEarning`, `PayrollCalculatorLopArrearsTest.earningArrearAddsToGross`, `deductionArrearReducesNet`, `lopAndArrearCombine` | Earning arrears add to gross; deduction arrears reduce net; LOP and arrears combine correctly in the same run | PASS |
| 6 | Supplementary Payroll | `PayrollRunServiceTest.startSupplementaryOnlyProcessesTheGivenEmployeesNotTheWholeCompany`, `startSupplementaryAllowsAnOpenPeriodUnlikeARegularRunWhichRequiresLocked`, `PayrollReportsServiceTest.supplementaryRegisterRejectsANonSupplementaryRun` (Sprint 16) | A supplementary run only processes the named employee subset, may run against an open period, and only a `SUPPLEMENTARY`-typed run may produce the supplementary register | PASS |
| 7 | Salary Revision | `EmployeeCompensationServiceTest.createSupersedesThePreviouslyActiveCompensationRecord`, `createAttachesCompensationLinesResolvedThroughThePayComponentCatalogue` | A new compensation record deactivates the prior one and resolves its pay-component lines through the catalogue | PASS |
| 8 | Loans | `PayrollCalculatorTest` (deduction-kind component processing); loans are modelled as a `DEDUCTION`-kind pay component (e.g. `LOAN_REPAY`) rather than a standalone module — `StatutoryDeductionServiceTest.extractForRunIgnoresDeductionsWithAnUnrecognisedCode` explicitly confirms `LOAN_REPAY` is correctly excluded from statutory extraction while still reducing net pay via the calculator | Loan repayment deduction reduces net; not misclassified as a statutory deduction | PASS |
| 9 | Reimbursements | `PayrollCalculatorTest` (earning-kind fixed/percentage component processing) — reimbursements are modelled as an `EARNING`-kind pay component, following the same generic calculation path exercised by `fixedEarningAddsToGross` | Reimbursement earning adds to gross | PASS |
| 10 | Leave Encashment | `FinalSettlementLifecycleTest.amountsDefaultToZero`, `FinalSettlementServiceTest.createStartsInDraftStatusWithZeroedOptionalAmounts` — leave encashment is a first-class amount field on `FinalSettlement`, aggregated into the settlement total alongside gratuity | Encashment amount defaults to zero and is explicitly settable per settlement | PASS |
| 11 | Bank File | `BankAdviceServiceTest` (Sprint 16: SKIPPED rules for zero-net/no-account, PENDING instruction generation, settlement roll-up), `BankAdviceCsvExporterTest` | One payment instruction per payslip; instructions materialize as CSV with the documented column set | PASS |
| 12 | GL Posting | `PayrollJournalServiceTest` (Sprint 16: balance enforcement, lifecycle guards, reconciliation), `PayrollJournalGeneratorTest`, `PayrollJournalCsvExporterTest` | Journal must balance (total debit = total credit) before approval; reconciliation flags the debit/credit delta and the expense-vs-run-gross delta | PASS |
| 13 | PF | `StatutoryClassifierTest.classifiesIndiaPf`, `StatutoryDeductionServiceTest.extractForRunTreatsPfAndProvidentFundCodesAsTheSameStatutoryObligation`, `extractForRunDerivesJurisdictionAndPeriodMonthFromTheClassifierAndPayslip` | `PF`/`PROVIDENT_FUND` codes both classify to jurisdiction `IN`, code `PF`; extraction derives period month and total from the payslip line | PASS |
| 14 | ESI | `StatutoryDeductionServiceTest.extractForRunClassifiesEsiAsIndianEsi` (Sprint 18 — closes the gap noted below) | `ESI` classifies to jurisdiction `IN`, code `ESI`; extraction carries the employee contribution and total amount through unchanged | PASS |
| 15 | PT | `StatutoryDeductionServiceTest.extractForRunClassifiesProfessionalTaxAsIndianPt` (Sprint 18 — named test added for symmetry with PF/ESI/TDS; previously only exercised mixed in with PF) | `PROFESSIONAL_TAX` classifies to jurisdiction `IN`, code `PT`, and is extracted alongside PF in the same run | PASS |
| 16 | TDS | `StatutoryDeductionServiceTest.extractForRunClassifiesTdsAsIndianTds` (Sprint 18 — closes the gap noted below) | `TDS` classifies to jurisdiction `IN`, code `TDS`; extraction carries the employee contribution and total amount through unchanged | PASS |
| 17 | Gratuity | `FinalSettlementLifecycleTest.amountsDefaultToZero`, `FinalSettlementServiceTest.createStartsInDraftStatusWithZeroedOptionalAmounts`, `approveQueuesGratuityAsAnEarningArrearWithTheGratuityReasonCode` (Sprint 18) | Gratuity amount defaults to zero and is explicitly settable per settlement; on approval it queues as an `EARNING`-kind `FFS_GRATUITY` arrear with the exact entered amount, included in the settlement total. **No statutory gratuity formula (e.g. 15/26 × last-drawn basic × years of service) exists in this codebase** — the amount is operator-entered, consistent with the "no new business functionality" constraint on this sprint; certifying an unwritten formula would be fabricating a requirement, not validating one | PASS |

## Fresh run confirming all of the above

```
mvn -o test -Dtest="com.ewos.payroll.**,com.ewos.exit.**,com.ewos.onboarding.**"
Tests run: 377, Failures: 0, Errors: 0     (Sprint 18 — was 373; +4 named certification tests below)
```

## Sprint 18 update — named certification tests added for PF/ESI/PT/TDS/Gratuity

Per Sprint 18's explicit remediation scope, the ESI/TDS gap flagged below was closed and PT/Gratuity
were given the same dedicated-by-name treatment PF already had, rather than leaving them certified
only "via shared code path":

- `StatutoryDeductionServiceTest.extractForRunClassifiesEsiAsIndianEsi` — closes the ESI gap.
- `StatutoryDeductionServiceTest.extractForRunClassifiesTdsAsIndianTds` — closes the TDS gap.
- `StatutoryDeductionServiceTest.extractForRunClassifiesProfessionalTaxAsIndianPt` — PT was
  previously only exercised mixed in with PF in one test; now has its own.
- `FinalSettlementServiceTest.approveQueuesGratuityAsAnEarningArrearWithTheGratuityReasonCode` —
  asserts gratuity queues as `EARNING`/`FFS_GRATUITY` on approval, mirroring the existing
  `DEDUCTION`/`FFS_NOTICE_RECOVERY` assertion for notice-pay recovery.

All four were run fresh as part of this sprint (see above) and pass. No statutory gratuity
*formula* (15/26 × last-drawn basic × years of service, or similar) exists anywhere in this
codebase — gratuity is an operator-entered amount on `FinalSettlement`, not a computed one. Adding
that formula would be new business functionality, out of scope for this sprint; what's certified
above is that the entered amount flows through settlement math correctly, which is the actual
behavior that exists to certify.

## Gaps identified (not fixed in this sprint — see Recommendation)

- **Loans and Reimbursements** are intentionally generic pay components (`DEDUCTION`/`EARNING`
  kind), not first-class domain modules — this is consistent with the "no new business modules"
  constraint for this sprint. Certification above rests on the generic `PayrollCalculatorTest`
  earning/deduction paths, which is correct given the architecture, but there is no dedicated test
  asserting a component literally coded `LOAN_REPAY` or `REIMBURSEMENT` flows through
  `PayrollCalculator` end-to-end (only that a generic deduction/earning of the same kind does).
  Low risk, same reasoning as above.
- **End-to-end (Docker-backed) certification** — the calculations above are certified at the unit/
  domain level in this sandbox. The `AbstractIntegrationTest`-based suite (Testcontainers +
  Postgres) that exercises full HTTP round-trips for these same flows cannot run here (no Docker)
  but does run in GitHub Actions CI, which is green on every commit pushed this sprint (see GitHub
  Verification section of the main Audit Readiness Report).
