package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateFinalSettlementRequest;
import com.ewos.payroll.api.dto.UpdateFinalSettlementRequest;
import com.ewos.payroll.domain.FinalSettlement;
import com.ewos.payroll.domain.FinalSettlementStatus;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.infrastructure.persistence.FinalSettlementRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Full &amp; Final settlement lifecycle: DRAFT -&gt; APPROVED -&gt; SETTLED / CANCELLED, arrear
 * queuing on approval, and the guards that keep locked records immutable.
 */
@ExtendWith(MockitoExtension.class)
class FinalSettlementServiceTest {

    @Mock FinalSettlementRepository repository;
    @Mock EmployeeRepository employees;
    @Mock PayrollArrearRepository arrears;
    @Mock PayrollRunService runs;
    @Mock ClientAccessGuard guard;

    private FinalSettlementService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new FinalSettlementService(
                        repository, employees, arrears, runs, new PayrollMapper(), guard);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));
        org.mockito.Mockito.lenient()
                .when(repository.save(any(FinalSettlement.class)))
                .thenAnswer(
                        inv -> {
                            FinalSettlement s = inv.getArgument(0);
                            if (s.getId() == null) {
                                s.setId(UUID.randomUUID());
                            }
                            return s;
                        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Employee employeeIn(UUID company) {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(company);
        return e;
    }

    private CreateFinalSettlementRequest request() {
        return new CreateFinalSettlementRequest(
                tenantId,
                companyId,
                employeeId,
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 3, 31),
                new BigDecimal("12"),
                new BigDecimal("15000"),
                new BigDecimal("50000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "USD",
                null);
    }

    private FinalSettlement draftSettlement() {
        FinalSettlement s = new FinalSettlement();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setCompanyId(companyId);
        s.setEmployee(employeeIn(companyId));
        s.setStatus(FinalSettlementStatus.DRAFT);
        s.setEncashmentAmount(new BigDecimal("15000"));
        s.setGratuityAmount(new BigDecimal("50000"));
        s.setNoticePayRecovery(BigDecimal.ZERO);
        s.setNoticePayReceivable(BigDecimal.ZERO);
        s.setOtherEarnings(BigDecimal.ZERO);
        s.setOtherDeductions(BigDecimal.ZERO);
        return s;
    }

    // --- create ---

    @Test
    void createRejectedWhenEmployeeDoesNotExist() {
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void createRejectedWhenEmployeeBelongsToADifferentCompany() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void createRejectedWhenALiveSettlementAlreadyExistsForTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(repository.findLiveForEmployee(tenantId, employeeId))
                .thenReturn(Optional.of(draftSettlement()));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createStartsInDraftStatusWithZeroedOptionalAmounts() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(repository.findLiveForEmployee(tenantId, employeeId)).thenReturn(Optional.empty());

        var response = service.create(request());

        assertThat(response.status()).isEqualTo(FinalSettlementStatus.DRAFT);
        assertThat(response.noticePayRecovery()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- update ---

    @Test
    void updateRejectedOnceSettlementIsNoLongerDraft() {
        FinalSettlement s = draftSettlement();
        s.setStatus(FinalSettlementStatus.APPROVED);
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        UpdateFinalSettlementRequest req =
                new UpdateFinalSettlementRequest(
                        null,
                        null,
                        null,
                        new BigDecimal("99999"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> service.update(tenantId, s.getId(), req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only DRAFT");
    }

    @Test
    void updateOnlyOverwritesSuppliedFields() {
        FinalSettlement s = draftSettlement();
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        UpdateFinalSettlementRequest req =
                new UpdateFinalSettlementRequest(
                        null,
                        null,
                        null,
                        new BigDecimal("18000"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        var response = service.update(tenantId, s.getId(), req);

        assertThat(response.encashmentAmount()).isEqualByComparingTo("18000");
        assertThat(response.gratuityAmount()).isEqualByComparingTo("50000");
    }

    // --- approve ---

    @Test
    void approveRejectedWhenNotInDraft() {
        FinalSettlement s = draftSettlement();
        s.setStatus(FinalSettlementStatus.SETTLED);
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.approve(tenantId, s.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only DRAFT");
    }

    @Test
    void approveRejectedWithoutAnAuthenticatedActor() {
        SecurityContextHolder.clearContext();
        FinalSettlement s = draftSettlement();
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.approve(tenantId, s.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void approveMovesToApprovedAndStampsApprover() {
        FinalSettlement s = draftSettlement();
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        var response = service.approve(tenantId, s.getId());

        assertThat(response.status()).isEqualTo(FinalSettlementStatus.APPROVED);
        assertThat(response.approvedAt()).isNotNull();
        assertThat(response.approvedBy()).isNotNull();
    }

    @Test
    void approveQueuesOnlyThePositiveAmountComponentsAsArrears() {
        FinalSettlement s = draftSettlement();
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        service.approve(tenantId, s.getId());

        // Only encashment (15000) and gratuity (50000) are positive; the other four are zero and
        // must be skipped, not queued as zero-amount arrears.
        verify(arrears, times(2)).save(any(PayrollArrear.class));
    }

    @Test
    void approveQueuesDeductionArrearsWithDeductionKind() {
        FinalSettlement s = draftSettlement();
        s.setNoticePayRecovery(new BigDecimal("8000"));
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        service.approve(tenantId, s.getId());

        verify(arrears).save(argThatKindIs(PayComponentKind.DEDUCTION, "FFS_NOTICE_RECOVERY"));
    }

    private static PayrollArrear argThatKindIs(PayComponentKind kind, String reasonCode) {
        return org.mockito.ArgumentMatchers.argThat(
                a -> a.getKind() == kind && reasonCode.equals(a.getReasonCode()));
    }

    // --- settle ---

    @Test
    void settleRejectedWhenNotApproved() {
        FinalSettlement s = draftSettlement();
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.settle(tenantId, s.getId(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only APPROVED");
    }

    @Test
    void settleStartsAFinalSettlementRunAndLocksTheRecord() {
        FinalSettlement s = draftSettlement();
        s.setStatus(FinalSettlementStatus.APPROVED);
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));
        UUID periodId = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(UUID.randomUUID());
        when(runs.startFinalSettlement(tenantId, companyId, periodId, employeeId)).thenReturn(run);

        var response = service.settle(tenantId, s.getId(), periodId);

        assertThat(response.status()).isEqualTo(FinalSettlementStatus.SETTLED);
        assertThat(response.settledAt()).isNotNull();
        verify(runs).startFinalSettlement(tenantId, companyId, periodId, employeeId);
    }

    // --- cancel ---

    @Test
    void cancelRejectedOnceSettled() {
        FinalSettlement s = draftSettlement();
        s.setStatus(FinalSettlementStatus.SETTLED);
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.cancel(tenantId, s.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void cancelAllowedFromDraft() {
        FinalSettlement s = draftSettlement();
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        var response = service.cancel(tenantId, s.getId());

        assertThat(response.status()).isEqualTo(FinalSettlementStatus.CANCELLED);
    }

    @Test
    void cancelAllowedFromApproved() {
        FinalSettlement s = draftSettlement();
        s.setStatus(FinalSettlementStatus.APPROVED);
        when(repository.findByIdAndTenantId(s.getId(), tenantId)).thenReturn(Optional.of(s));

        var response = service.cancel(tenantId, s.getId());

        assertThat(response.status()).isEqualTo(FinalSettlementStatus.CANCELLED);
    }

    // --- access control / not-found ---

    @Test
    void getByIdThrowsNotFoundForAnUnknownSettlement() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void byStatusChecksAccessForTheRequestedCompany() {
        when(repository.findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
                        tenantId, companyId, FinalSettlementStatus.DRAFT))
                .thenReturn(List.of());

        service.byStatus(tenantId, companyId, FinalSettlementStatus.DRAFT);

        verify(guard).requireAccessForCompany(companyId);
    }
}
