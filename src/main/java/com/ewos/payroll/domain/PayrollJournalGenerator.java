package com.ewos.payroll.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.payroll.infrastructure.persistence.EmployeeCostAllocationRepository;
import com.ewos.payroll.infrastructure.persistence.GLMappingRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryDeductionRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Turns a set of finalized payslips into balanced double-entry journal lines using the tenant's GL
 * mappings and per-employee cost allocations. The generator does not touch persistence — it emits
 * {@link PayrollJournalLine}s that the caller wires onto a fresh {@link PayrollJournal}.
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>For each payslip line, look up the mapping by ({@link GLMappingSourceKind#PAY_COMPONENT},
 *       component code). Earning components debit the expense account; deduction components credit
 *       the liability / clearing account.
 *   <li>For each statutory deduction on the payslip, look up ({@link
 *       GLMappingSourceKind#STATUTORY}, statutory code). Employee portion credits the statutory
 *       liability; employer contribution debits an expense and credits the liability.
 *   <li>Emit a balancing NET_PAY line: total employee net = credit to the salary-payable /
 *       bank-clearing account referenced by {@link GLMappingSourceKind#NET_PAY} / {@code NET}.
 *   <li>If a mapping's {@link AllocationDimension} is anything other than {@code NONE}, the expense
 *       debit is split proportionally across the employee's active cost allocations.
 * </ol>
 *
 * <p>If a required mapping is missing the generator throws — payroll accounting must be complete,
 * so silent skipping would corrupt the trial balance.
 */
@Component
public final class PayrollJournalGenerator {

    private static final int MONEY_SCALE = 4;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final GLMappingRepository mappings;
    private final EmployeeCostAllocationRepository allocations;
    private final StatutoryDeductionRepository statutory;

    public PayrollJournalGenerator(
            GLMappingRepository mappings,
            EmployeeCostAllocationRepository allocations,
            StatutoryDeductionRepository statutory) {
        this.mappings = mappings;
        this.allocations = allocations;
        this.statutory = statutory;
    }

    public GenerationResult generate(
            UUID tenantId, UUID companyId, List<Payslip> payslips, String currency) {
        List<PayrollJournalLine> out = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int lineNo = 1;

        for (Payslip slip : payslips) {
            List<EmployeeCostAllocation> costAllocations =
                    allocations.findActiveForEmployee(tenantId, slip.getEmployee().getId());
            for (PayslipLine line : slip.getLines()) {
                GLMapping m =
                        mappings.findActive(
                                        tenantId,
                                        companyId,
                                        GLMappingSourceKind.PAY_COMPONENT,
                                        line.getComponentCodeSnapshot())
                                .orElseThrow(
                                        () ->
                                                new ApiException(
                                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                                        "No GL mapping for component "
                                                                + line.getComponentCodeSnapshot()));
                addLinesForMapping(
                        out,
                        m,
                        line.getAmount(),
                        slip.getEmployee(),
                        costAllocations,
                        currency,
                        lineNo,
                        "payslip=" + slip.getId(),
                        line.getComponentCodeSnapshot());
                lineNo += 2;
            }

            for (StatutoryDeduction d : statutory.findAllForPayslip(tenantId, slip.getId())) {
                if (d.getEmployerContribution() != null
                        && d.getEmployerContribution().signum() > 0) {
                    GLMapping em =
                            mappings.findActive(
                                            tenantId,
                                            companyId,
                                            GLMappingSourceKind.EMPLOYER_CONTRIBUTION,
                                            d.getCode())
                                    .orElseThrow(
                                            () ->
                                                    new ApiException(
                                                            HttpStatus.UNPROCESSABLE_ENTITY,
                                                            "No employer-contribution mapping for "
                                                                    + d.getCode()));
                    addLinesForMapping(
                            out,
                            em,
                            d.getEmployerContribution(),
                            slip.getEmployee(),
                            costAllocations,
                            currency,
                            lineNo,
                            "statutory=" + d.getId(),
                            "EMPR_" + d.getCode());
                    lineNo += 2;
                }
            }
        }

        BigDecimal grandNet =
                payslips.stream()
                        .map(Payslip::getNetAmount)
                        .filter(v -> v != null && v.signum() > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grandNet.signum() > 0) {
            GLMapping netMap =
                    mappings.findActive(tenantId, companyId, GLMappingSourceKind.NET_PAY, "NET")
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.UNPROCESSABLE_ENTITY,
                                                    "No NET_PAY mapping (expected source_code=NET)"));
            out.add(
                    debitLine(
                            netMap.getDebitAccount(),
                            grandNet,
                            currency,
                            lineNo++,
                            PayrollJournalLineSourceKind.NET_PAY,
                            "NET",
                            "Payroll clearing"));
            out.add(
                    creditLine(
                            netMap.getCreditAccount(),
                            grandNet,
                            currency,
                            lineNo,
                            PayrollJournalLineSourceKind.BALANCING,
                            "NET",
                            "Net payable to employees"));
        }

        for (PayrollJournalLine l : out) {
            totalDebit = totalDebit.add(l.getDebitAmount());
            totalCredit = totalCredit.add(l.getCreditAmount());
        }
        return new GenerationResult(out, scale(totalDebit), scale(totalCredit));
    }

    private static void addLinesForMapping(
            List<PayrollJournalLine> out,
            GLMapping m,
            BigDecimal amount,
            Employee employee,
            List<EmployeeCostAllocation> costAllocations,
            String currency,
            int startingLineNo,
            String sourceRef,
            String desc) {
        BigDecimal scaled = scale(amount);
        if (m.getAllocationDimension() == AllocationDimension.NONE || costAllocations.isEmpty()) {
            out.add(
                    debitLine(
                            m.getDebitAccount(),
                            scaled,
                            currency,
                            startingLineNo,
                            resolveSourceKind(m),
                            sourceRef,
                            desc));
            out.add(
                    creditLine(
                            m.getCreditAccount(),
                            scaled,
                            currency,
                            startingLineNo + 1,
                            resolveSourceKind(m),
                            sourceRef,
                            desc));
            return;
        }
        BigDecimal remaining = scaled;
        for (int i = 0; i < costAllocations.size(); i++) {
            EmployeeCostAllocation a = costAllocations.get(i);
            BigDecimal share =
                    (i == costAllocations.size() - 1)
                            ? remaining
                            : scale(
                                    scaled.multiply(a.getPercentage())
                                            .divide(
                                                    ONE_HUNDRED,
                                                    MONEY_SCALE,
                                                    RoundingMode.HALF_UP));
            remaining = remaining.subtract(share);
            PayrollJournalLine dr =
                    debitLine(
                            m.getDebitAccount(),
                            share,
                            currency,
                            startingLineNo,
                            resolveSourceKind(m),
                            sourceRef,
                            desc);
            applyDimension(dr, m.getAllocationDimension(), a, employee);
            out.add(dr);
        }
        out.add(
                creditLine(
                        m.getCreditAccount(),
                        scaled,
                        currency,
                        startingLineNo + 1,
                        resolveSourceKind(m),
                        sourceRef,
                        desc));
    }

    private static void applyDimension(
            PayrollJournalLine line,
            AllocationDimension dim,
            EmployeeCostAllocation a,
            Employee employee) {
        switch (dim) {
            case COST_CENTRE:
                if (a.getCostCentre() != null) {
                    line.setCostCentreCode(a.getCostCentre().getCode());
                }
                break;
            case BUSINESS_UNIT:
                if (a.getBusinessUnit() != null) {
                    line.setBusinessUnitCode(a.getBusinessUnit().getCode());
                }
                break;
            case DEPARTMENT:
                OrganizationUnit dept =
                        a.getDepartmentOrgUnit() != null
                                ? a.getDepartmentOrgUnit()
                                : employee.getPrimaryOrgUnit();
                if (dept != null) {
                    line.setDepartmentCode(dept.getCode());
                }
                break;
            case NONE:
            default:
                break;
        }
    }

    private static PayrollJournalLineSourceKind resolveSourceKind(GLMapping m) {
        return switch (m.getSourceKind()) {
            case PAY_COMPONENT -> PayrollJournalLineSourceKind.PAY_COMPONENT;
            case EMPLOYER_CONTRIBUTION -> PayrollJournalLineSourceKind.EMPLOYER_CONTRIBUTION;
            case NET_PAY -> PayrollJournalLineSourceKind.NET_PAY;
            case PROVISION -> PayrollJournalLineSourceKind.PROVISION;
            case ACCRUAL -> PayrollJournalLineSourceKind.ACCRUAL;
            case STATUTORY -> PayrollJournalLineSourceKind.STATUTORY;
        };
    }

    private static PayrollJournalLine debitLine(
            GLAccount acct,
            BigDecimal amount,
            String currency,
            int lineNo,
            PayrollJournalLineSourceKind kind,
            String sourceRef,
            String desc) {
        PayrollJournalLine l = base(acct, currency, lineNo, kind, sourceRef, desc);
        l.setDebitAmount(scale(amount));
        return l;
    }

    private static PayrollJournalLine creditLine(
            GLAccount acct,
            BigDecimal amount,
            String currency,
            int lineNo,
            PayrollJournalLineSourceKind kind,
            String sourceRef,
            String desc) {
        PayrollJournalLine l = base(acct, currency, lineNo, kind, sourceRef, desc);
        l.setCreditAmount(scale(amount));
        return l;
    }

    private static PayrollJournalLine base(
            GLAccount acct,
            String currency,
            int lineNo,
            PayrollJournalLineSourceKind kind,
            String sourceRef,
            String desc) {
        PayrollJournalLine l = new PayrollJournalLine();
        l.setLineNo(lineNo);
        l.setGlAccountCode(acct.getCode());
        l.setGlAccountNameSnapshot(acct.getName());
        l.setAccountTypeSnapshot(acct.getAccountType());
        l.setCurrency(currency);
        l.setSourceKind(kind);
        l.setSourceReference(sourceRef);
        l.setDescription(desc);
        return l;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Container for a generated journal — lines plus running totals. */
    public record GenerationResult(
            List<PayrollJournalLine> lines, BigDecimal totalDebit, BigDecimal totalCredit) {
        public boolean isBalanced() {
            return totalDebit.compareTo(totalCredit) == 0;
        }
    }
}
