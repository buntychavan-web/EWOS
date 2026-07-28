package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.RollUpChallanRequest;
import com.ewos.payroll.domain.StatutoryChallan;
import com.ewos.payroll.domain.StatutoryChallanStatus;
import com.ewos.payroll.domain.StatutoryDeduction;
import com.ewos.payroll.infrastructure.persistence.StatutoryChallanRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryDeductionRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Rolling statutory deductions into a monthly challan: idempotent scope resolution, aggregation
 * math, unique-employee counting, and the file -&gt; pay lifecycle guards.
 */
@ExtendWith(MockitoExtension.class)
class StatutoryChallanServiceTest {

    @Mock StatutoryChallanRepository challans;
    @Mock StatutoryDeductionRepository deductions;
    @Mock ClientAccessGuard guard;

    private StatutoryChallanService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StatutoryChallanService(challans, deductions, new PayrollMapper(), guard);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));
        org.mockito.Mockito.lenient()
                .when(challans.save(any(StatutoryChallan.class)))
                .thenAnswer(
                        inv -> {
                            StatutoryChallan c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private RollUpChallanRequest request() {
        return new RollUpChallanRequest(tenantId, companyId, "IN", "PF", 202603);
    }

    private StatutoryDeduction deductionFor(UUID employeeId, String amount) {
        StatutoryDeduction d = new StatutoryDeduction();
        Employee e = new Employee();
        e.setId(employeeId);
        d.setEmployee(e);
        d.setTaxableBase(new BigDecimal("50000"));
        d.setEmployeeContribution(new BigDecimal(amount));
        d.setEmployerContribution(new BigDecimal(amount));
        d.setTotalAmount(new BigDecimal(amount).multiply(new BigDecimal("2")));
        d.setCurrency("INR");
        return d;
    }

    @Test
    void rollUpCreatesAFreshDraftChallanWhenNoneExistsForTheScope() {
        when(challans.findByScope(tenantId, companyId, "IN", "PF", 202603))
                .thenReturn(Optional.empty());
        when(deductions.findUnattachedForScope(tenantId, companyId, "IN", "PF", 202603))
                .thenReturn(List.of());

        var response = service.rollUp(request());

        assertThat(response.status()).isEqualTo(StatutoryChallanStatus.DRAFT);
        assertThat(response.jurisdiction()).isEqualTo("IN");
        assertThat(response.code()).isEqualTo("PF");
    }

    @Test
    void rollUpRejectsAppendingToAChallanThatIsNoLongerDraft() {
        StatutoryChallan existing = new StatutoryChallan();
        existing.setId(UUID.randomUUID());
        existing.setStatus(StatutoryChallanStatus.FILED);
        when(challans.findByScope(tenantId, companyId, "IN", "PF", 202603))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.rollUp(request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rollUpAggregatesAmountsAndCountsUniqueEmployeesOnce() {
        when(challans.findByScope(tenantId, companyId, "IN", "PF", 202603))
                .thenReturn(Optional.empty());
        UUID emp1 = UUID.randomUUID();
        UUID emp2 = UUID.randomUUID();
        when(deductions.findUnattachedForScope(tenantId, companyId, "IN", "PF", 202603))
                .thenReturn(
                        List.of(
                                deductionFor(emp1, "1800"),
                                deductionFor(emp2, "1800"),
                                deductionFor(emp1, "1800")));

        var response = service.rollUp(request());

        assertThat(response.totalEmployees()).isEqualTo(2);
        assertThat(response.totalEmployeeContribution()).isEqualByComparingTo("5400");
        assertThat(response.totalAmount()).isEqualByComparingTo("10800");
        assertThat(response.currency()).isEqualTo("INR");
    }

    @Test
    void rollUpCalledTwiceIsIdempotentAndOnlyAddsNewlyEligibleDeductions() {
        StatutoryChallan existing = new StatutoryChallan();
        existing.setId(UUID.randomUUID());
        existing.setStatus(StatutoryChallanStatus.DRAFT);
        existing.setTotalEmployees(1);
        existing.setTotalEmployeeContribution(new BigDecimal("1800"));
        existing.setTotalAmount(new BigDecimal("3600"));
        when(challans.findByScope(tenantId, companyId, "IN", "PF", 202603))
                .thenReturn(Optional.of(existing));
        UUID newEmployee = UUID.randomUUID();
        when(deductions.findUnattachedForScope(tenantId, companyId, "IN", "PF", 202603))
                .thenReturn(List.of(deductionFor(newEmployee, "1800")));

        var response = service.rollUp(request());

        assertThat(response.totalEmployees()).isEqualTo(2);
        assertThat(response.totalEmployeeContribution()).isEqualByComparingTo("3600");
    }

    @Test
    void fileRejectedWhenChallanIsNotDraft() {
        UUID id = UUID.randomUUID();
        StatutoryChallan c = new StatutoryChallan();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setStatus(StatutoryChallanStatus.PAID);
        when(challans.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.file(tenantId, id, "REF-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only DRAFT");
    }

    @Test
    void fileMovesADraftChallanToFiledWithAReference() {
        UUID id = UUID.randomUUID();
        StatutoryChallan c = new StatutoryChallan();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setStatus(StatutoryChallanStatus.DRAFT);
        when(challans.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(c));

        var response = service.file(tenantId, id, "REF-2026-03-PF");

        assertThat(response.status()).isEqualTo(StatutoryChallanStatus.FILED);
        assertThat(response.filingReference()).isEqualTo("REF-2026-03-PF");
        assertThat(response.filedBy()).isNotNull();
    }

    @Test
    void payRejectedWhenChallanIsNotFiled() {
        UUID id = UUID.randomUUID();
        StatutoryChallan c = new StatutoryChallan();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setStatus(StatutoryChallanStatus.DRAFT);
        when(challans.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.pay(tenantId, id, "PAY-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only FILED");
    }

    @Test
    void payMovesAFiledChallanToPaid() {
        UUID id = UUID.randomUUID();
        StatutoryChallan c = new StatutoryChallan();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setStatus(StatutoryChallanStatus.FILED);
        when(challans.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(c));

        var response = service.pay(tenantId, id, "UTR-998877");

        assertThat(response.status()).isEqualTo(StatutoryChallanStatus.PAID);
        assertThat(response.paymentReference()).isEqualTo("UTR-998877");
    }

    @Test
    void cancelRejectsAnAlreadyPaidChallan() {
        UUID id = UUID.randomUUID();
        StatutoryChallan c = new StatutoryChallan();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setStatus(StatutoryChallanStatus.PAID);
        when(challans.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.cancel(tenantId, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void cancelAllowedFromDraftOrFiled() {
        UUID id = UUID.randomUUID();
        StatutoryChallan c = new StatutoryChallan();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setStatus(StatutoryChallanStatus.FILED);
        when(challans.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(c));

        var response = service.cancel(tenantId, id);

        assertThat(response.status()).isEqualTo(StatutoryChallanStatus.CANCELLED);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownChallan() {
        UUID id = UUID.randomUUID();
        when(challans.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forMonthChecksAccessForTheRequestedCompany() {
        when(challans.findAllByTenantIdAndCompanyIdAndPeriodMonthOrderByCodeAsc(
                        tenantId, companyId, 202603))
                .thenReturn(List.of());

        service.forMonth(tenantId, companyId, 202603);

        verify(guard).requireAccessForCompany(companyId);
    }
}
