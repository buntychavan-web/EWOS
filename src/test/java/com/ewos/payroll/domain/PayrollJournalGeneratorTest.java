package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.infrastructure.persistence.EmployeeCostAllocationRepository;
import com.ewos.payroll.infrastructure.persistence.GLMappingRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryDeductionRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Double-entry GL journal generation from finalized payslips: balancing, missing-mapping failure
 * (accounting must be complete, never silently skipped), employer-contribution lines, and
 * proportional cost-centre splitting.
 */
@ExtendWith(MockitoExtension.class)
class PayrollJournalGeneratorTest {

    @Mock GLMappingRepository mappings;
    @Mock EmployeeCostAllocationRepository allocations;
    @Mock StatutoryDeductionRepository statutory;

    private PayrollJournalGenerator generator;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        generator = new PayrollJournalGenerator(mappings, allocations, statutory);
    }

    private GLAccount account(String code, GLAccountType type) {
        GLAccount a = new GLAccount();
        a.setCode(code);
        a.setName(code);
        a.setAccountType(type);
        return a;
    }

    private GLMapping mapping(GLAccount debit, GLAccount credit, AllocationDimension dim) {
        GLMapping m = new GLMapping();
        m.setDebitAccount(debit);
        m.setCreditAccount(credit);
        m.setAllocationDimension(dim);
        m.setSourceKind(GLMappingSourceKind.PAY_COMPONENT);
        return m;
    }

    private Payslip payslipWithBasicLine(BigDecimal amount) {
        Employee emp = new Employee();
        emp.setId(UUID.randomUUID());
        Payslip slip = new Payslip();
        slip.setId(UUID.randomUUID());
        slip.setEmployee(emp);
        slip.setNetAmount(amount);
        PayslipLine line = new PayslipLine();
        line.setComponentCodeSnapshot("BASIC");
        line.setKind(PayComponentKind.EARNING);
        line.setAmount(amount);
        slip.addLine(line);
        return slip;
    }

    @Test
    void generateThrowsWhenAPayComponentHasNoGlMappingRatherThanSkippingIt() {
        Payslip slip = payslipWithBasicLine(new BigDecimal("50000"));
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.PAY_COMPONENT, "BASIC"))
                .thenReturn(Optional.empty());
        when(allocations.findActiveForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> generator.generate(tenantId, companyId, List.of(slip), "INR"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No GL mapping for component BASIC");
    }

    @Test
    void generateProducesABalancedJournalForASingleEarningLine() {
        Payslip slip = payslipWithBasicLine(new BigDecimal("50000"));
        GLMapping basicMapping =
                mapping(
                        account("SALARY_EXPENSE", GLAccountType.EXPENSE),
                        account("SALARY_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.NONE);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.PAY_COMPONENT, "BASIC"))
                .thenReturn(Optional.of(basicMapping));
        when(allocations.findActiveForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(List.of());
        when(statutory.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());
        GLMapping netMapping =
                mapping(
                        account("PAYROLL_CLEARING", GLAccountType.ASSET),
                        account("NET_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.NONE);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.NET_PAY, "NET"))
                .thenReturn(Optional.of(netMapping));

        var result = generator.generate(tenantId, companyId, List.of(slip), "INR");

        assertThat(result.isBalanced()).isTrue();
        // BASIC debit+credit pair, plus NET_PAY debit+credit pair.
        assertThat(result.lines()).hasSize(4);
        assertThat(result.totalDebit()).isEqualByComparingTo("100000.0000");
    }

    @Test
    void generateOmitsTheNetPayLinesWhenNoEmployeeHasAPositiveNetAmount() {
        Payslip slip = payslipWithBasicLine(BigDecimal.ZERO);
        GLMapping basicMapping =
                mapping(
                        account("SALARY_EXPENSE", GLAccountType.EXPENSE),
                        account("SALARY_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.NONE);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.PAY_COMPONENT, "BASIC"))
                .thenReturn(Optional.of(basicMapping));
        when(allocations.findActiveForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(List.of());
        when(statutory.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        var result = generator.generate(tenantId, companyId, List.of(slip), "INR");

        assertThat(result.lines()).hasSize(2);
        assertThat(result.isBalanced()).isTrue();
    }

    @Test
    void generateAddsEmployerContributionLinesOnlyWhenThePortionIsPositive() {
        Payslip slip = payslipWithBasicLine(new BigDecimal("50000"));
        GLMapping basicMapping =
                mapping(
                        account("SALARY_EXPENSE", GLAccountType.EXPENSE),
                        account("SALARY_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.NONE);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.PAY_COMPONENT, "BASIC"))
                .thenReturn(Optional.of(basicMapping));
        when(allocations.findActiveForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(List.of());

        StatutoryDeduction pfWithEmployerShare = new StatutoryDeduction();
        pfWithEmployerShare.setCode("PF");
        pfWithEmployerShare.setEmployerContribution(new BigDecimal("1800"));
        StatutoryDeduction ptWithNoEmployerShare = new StatutoryDeduction();
        ptWithNoEmployerShare.setCode("PT");
        ptWithNoEmployerShare.setEmployerContribution(BigDecimal.ZERO);
        when(statutory.findAllForPayslip(tenantId, slip.getId()))
                .thenReturn(List.of(pfWithEmployerShare, ptWithNoEmployerShare));

        GLMapping pfEmployerMapping =
                mapping(
                        account("PF_EMPLOYER_EXPENSE", GLAccountType.EXPENSE),
                        account("PF_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.NONE);
        when(mappings.findActive(
                        tenantId, companyId, GLMappingSourceKind.EMPLOYER_CONTRIBUTION, "PF"))
                .thenReturn(Optional.of(pfEmployerMapping));
        GLMapping netMapping =
                mapping(
                        account("PAYROLL_CLEARING", GLAccountType.ASSET),
                        account("NET_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.NONE);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.NET_PAY, "NET"))
                .thenReturn(Optional.of(netMapping));

        var result = generator.generate(tenantId, companyId, List.of(slip), "INR");

        // BASIC pair + PF-employer pair + NET_PAY pair; PT is skipped since its employer share is
        // zero.
        assertThat(result.lines()).hasSize(6);
        assertThat(result.isBalanced()).isTrue();
    }

    @Test
    void generateThrowsWhenAnEmployerContributionHasNoMapping() {
        Payslip slip = payslipWithBasicLine(new BigDecimal("50000"));
        GLMapping basicMapping =
                mapping(
                        account("SALARY_EXPENSE", GLAccountType.EXPENSE),
                        account("SALARY_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.NONE);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.PAY_COMPONENT, "BASIC"))
                .thenReturn(Optional.of(basicMapping));
        when(allocations.findActiveForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(List.of());
        StatutoryDeduction pf = new StatutoryDeduction();
        pf.setCode("PF");
        pf.setEmployerContribution(new BigDecimal("1800"));
        when(statutory.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of(pf));
        when(mappings.findActive(
                        tenantId, companyId, GLMappingSourceKind.EMPLOYER_CONTRIBUTION, "PF"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> generator.generate(tenantId, companyId, List.of(slip), "INR"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No employer-contribution mapping for PF");
    }

    @Test
    void generateSplitsTheDebitProportionallyAcrossCostCentreAllocationsAndCreditsOnce() {
        Payslip slip = payslipWithBasicLine(new BigDecimal("50000"));
        GLMapping basicMapping =
                mapping(
                        account("SALARY_EXPENSE", GLAccountType.EXPENSE),
                        account("SALARY_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.COST_CENTRE);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.PAY_COMPONENT, "BASIC"))
                .thenReturn(Optional.of(basicMapping));

        CostCentre engineering = new CostCentre();
        engineering.setCode("CC-ENG");
        CostCentre sales = new CostCentre();
        sales.setCode("CC-SALES");
        EmployeeCostAllocation a1 = new EmployeeCostAllocation();
        a1.setCostCentre(engineering);
        a1.setPercentage(new BigDecimal("60"));
        EmployeeCostAllocation a2 = new EmployeeCostAllocation();
        a2.setCostCentre(sales);
        a2.setPercentage(new BigDecimal("40"));
        when(allocations.findActiveForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(List.of(a1, a2));
        when(statutory.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());
        GLMapping netMapping =
                mapping(
                        account("PAYROLL_CLEARING", GLAccountType.ASSET),
                        account("NET_PAYABLE", GLAccountType.LIABILITY),
                        AllocationDimension.NONE);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.NET_PAY, "NET"))
                .thenReturn(Optional.of(netMapping));

        var result = generator.generate(tenantId, companyId, List.of(slip), "INR");

        // Two proportional debit lines (60/40 split) plus one credit line for BASIC, plus the
        // NET_PAY debit/credit pair.
        assertThat(result.lines()).hasSize(5);
        assertThat(result.isBalanced()).isTrue();
        List<PayrollJournalLine> debits =
                result.lines().stream().filter(l -> l.getCostCentreCode() != null).toList();
        assertThat(debits).hasSize(2);
        assertThat(debits.get(0).getCostCentreCode()).isEqualTo("CC-ENG");
        assertThat(debits.get(0).getDebitAmount()).isEqualByComparingTo("30000.0000");
        assertThat(debits.get(1).getCostCentreCode()).isEqualTo("CC-SALES");
        assertThat(debits.get(1).getDebitAmount()).isEqualByComparingTo("20000.0000");
    }
}
