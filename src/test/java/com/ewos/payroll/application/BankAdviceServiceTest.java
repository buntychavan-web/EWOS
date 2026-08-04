package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.GenerateBankAdviceRequest;
import com.ewos.payroll.domain.BankAdvice;
import com.ewos.payroll.domain.BankAdviceCsvExporter;
import com.ewos.payroll.domain.BankAdviceFormat;
import com.ewos.payroll.domain.BankAdviceStatus;
import com.ewos.payroll.domain.EmployeeBankAccount;
import com.ewos.payroll.domain.PaymentInstruction;
import com.ewos.payroll.domain.PaymentInstructionStatus;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.BankAdviceRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeBankAccountRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
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
 * Bank advice orchestration: run gating before generation, the per-payslip SKIPPED rules (zero net,
 * no primary bank account), settlement roll-up to SETTLED once every instruction resolves, and the
 * per-instruction PAID/FAILED transitions.
 */
@ExtendWith(MockitoExtension.class)
class BankAdviceServiceTest {

    @Mock BankAdviceRepository advices;
    @Mock PayrollRunRepository runs;
    @Mock PayslipRepository payslips;
    @Mock EmployeeBankAccountRepository bankAccounts;
    @Mock ClientAccessGuard guard;
    private final BankAdviceCsvExporter csv = new BankAdviceCsvExporter();
    private final PayrollMapper mapper = new PayrollMapper();

    private BankAdviceService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BankAdviceService(advices, runs, payslips, bankAccounts, csv, mapper, guard);
        org.mockito.Mockito.lenient()
                .when(advices.save(any(BankAdvice.class)))
                .thenAnswer(
                        inv -> {
                            BankAdvice a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId(UUID.randomUUID());
                            }
                            return a;
                        });
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actorId.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private PayrollRun run(PayrollRunStatus status) {
        PayrollRun r = new PayrollRun();
        r.setId(runId);
        r.setCompanyId(companyId);
        r.setStatus(status);
        return r;
    }

    private Payslip payslip(BigDecimal netAmount) {
        Payslip p = new Payslip();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setCurrency("INR");
        p.setNetAmount(netAmount);
        p.setEmployeeNameSnapshot("Asha Rao");
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        p.setEmployee(e);
        return p;
    }

    private GenerateBankAdviceRequest request(String adviceNumber) {
        return new GenerateBankAdviceRequest(
                tenantId,
                companyId,
                runId,
                adviceNumber,
                LocalDate.of(2026, 4, 1),
                BankAdviceFormat.CSV,
                null);
    }

    @Test
    void generateChecksCompanyAccessFirst() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(request("ADV-1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void generateRejectsARunThatIsNotFinalizedOrFrozen() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.PROCESSING)));

        assertThatThrownBy(() -> service.generate(request("ADV-1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generateRejectsADuplicateAdviceNumber() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));
        when(advices.existsByTenantIdAndCompanyIdAndAdviceNumberIgnoreCase(
                        tenantId, companyId, "ADV-1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.generate(request("ADV-1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generateSkipsAPayslipWithZeroNetAmount() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(payslip(BigDecimal.ZERO)));

        var response = service.generate(request("ADV-1"));

        assertThat(response.totalCount()).isZero();
        assertThat(response.instructions()).hasSize(1);
        assertThat(response.instructions().get(0).status())
                .isEqualTo(PaymentInstructionStatus.SKIPPED);
    }

    @Test
    void generateSkipsAnEmployeeWithNoPrimaryBankAccount() {
        Payslip slip = payslip(new BigDecimal("500.00"));
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(bankAccounts.findPrimaryForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(Optional.empty());

        var response = service.generate(request("ADV-1"));

        assertThat(response.totalCount()).isZero();
        assertThat(response.instructions().get(0).status())
                .isEqualTo(PaymentInstructionStatus.SKIPPED);
        assertThat(response.instructions().get(0).failureReason())
                .isEqualTo("Employee has no primary bank account");
    }

    @Test
    void generateBuildsAPendingInstructionWhenAPrimaryAccountExists() {
        Payslip slip = payslip(new BigDecimal("500.00"));
        EmployeeBankAccount account = new EmployeeBankAccount();
        account.setId(UUID.randomUUID());
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(bankAccounts.findPrimaryForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(Optional.of(account));

        var response = service.generate(request("ADV-1"));

        assertThat(response.status()).isEqualTo(BankAdviceStatus.GENERATED);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("500.00");
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.instructions().get(0).status())
                .isEqualTo(PaymentInstructionStatus.PENDING);
    }

    @Test
    void generateCapturesTheRealAccountNumberNotJustTheMaskedOne() {
        Payslip slip = payslip(new BigDecimal("500.00"));
        EmployeeBankAccount account = new EmployeeBankAccount();
        account.setId(UUID.randomUUID());
        account.setAccountNumber("1234567890123");
        account.setAccountNumberMasked("*********0123");
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(bankAccounts.findPrimaryForEmployee(tenantId, slip.getEmployee().getId()))
                .thenReturn(Optional.of(account));

        var response = service.generate(request("ADV-1"));

        var instruction = response.instructions().get(0);
        assertThat(instruction.accountNumberMasked()).isEqualTo("*********0123");

        java.util.List<PaymentInstruction> saved = capturedInstructions(response.id());
        assertThat(saved.get(0).getAccountNumberSnapshot()).isEqualTo("1234567890123");
    }

    private java.util.List<PaymentInstruction> capturedInstructions(UUID adviceId) {
        org.mockito.ArgumentCaptor<BankAdvice> captor =
                org.mockito.ArgumentCaptor.forClass(BankAdvice.class);
        verify(advices).save(captor.capture());
        return captor.getValue().getInstructions();
    }

    @Test
    void acknowledgeRejectsAnAdviceThatIsNotGenerated() {
        BankAdvice a = new BankAdvice();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        a.setStatus(BankAdviceStatus.DRAFT);
        when(advices.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.acknowledge(tenantId, a.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void markInstructionPaidTransitionsToSettledWhenNoInstructionsRemainPending() {
        BankAdvice a = new BankAdvice();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        a.setStatus(BankAdviceStatus.GENERATED);
        PaymentInstruction only = new PaymentInstruction();
        only.setId(UUID.randomUUID());
        only.setStatus(PaymentInstructionStatus.PENDING);
        a.addInstruction(only);
        when(advices.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        var response = service.markInstructionPaid(tenantId, a.getId(), only.getId(), "UTR-1");

        assertThat(response.status()).isEqualTo(BankAdviceStatus.SETTLED);
        assertThat(only.getSettlementReference()).isEqualTo("UTR-1");
    }

    @Test
    void markInstructionPaidLeavesAdviceGeneratedWhileOtherInstructionsArePending() {
        BankAdvice a = new BankAdvice();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        a.setStatus(BankAdviceStatus.GENERATED);
        PaymentInstruction first = new PaymentInstruction();
        first.setId(UUID.randomUUID());
        first.setStatus(PaymentInstructionStatus.PENDING);
        PaymentInstruction second = new PaymentInstruction();
        second.setId(UUID.randomUUID());
        second.setStatus(PaymentInstructionStatus.PENDING);
        a.addInstruction(first);
        a.addInstruction(second);
        when(advices.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        var response = service.markInstructionPaid(tenantId, a.getId(), first.getId(), "UTR-1");

        assertThat(response.status()).isEqualTo(BankAdviceStatus.GENERATED);
    }

    @Test
    void markInstructionPaidRejectsAnInstructionThatIsNotPending() {
        BankAdvice a = new BankAdvice();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        a.setStatus(BankAdviceStatus.GENERATED);
        PaymentInstruction already = new PaymentInstruction();
        already.setId(UUID.randomUUID());
        already.setStatus(PaymentInstructionStatus.PAID);
        a.addInstruction(already);
        when(advices.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(
                        () ->
                                service.markInstructionPaid(
                                        tenantId, a.getId(), already.getId(), "UTR-2"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void markInstructionFailedThrowsNotFoundForAnUnknownInstruction() {
        BankAdvice a = new BankAdvice();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        when(advices.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(
                        () ->
                                service.markInstructionFailed(
                                        tenantId, a.getId(), UUID.randomUUID(), "bounced"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markFailedRejectsASettledAdvice() {
        BankAdvice a = new BankAdvice();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        a.setStatus(BankAdviceStatus.SETTLED);
        when(advices.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.markFailed(tenantId, a.getId(), "bank rejected file"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void forRunChecksAccessForEveryDistinctCompanyAmongTheResults() {
        BankAdvice a = new BankAdvice();
        a.setCompanyId(companyId);
        when(advices.findAllForRun(tenantId, runId)).thenReturn(List.of(a));

        service.forRun(tenantId, runId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void exportDelegatesToTheCsvExporter() {
        BankAdvice a = new BankAdvice();
        a.setId(UUID.randomUUID());
        a.setCompanyId(companyId);
        a.setAdviceNumber("ADV-1");
        when(advices.findByIdAndTenantId(a.getId(), tenantId)).thenReturn(Optional.of(a));

        String out = service.export(tenantId, a.getId());

        assertThat(out).startsWith("advice_number,employee_number");
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownAdvice() {
        UUID id = UUID.randomUUID();
        when(advices.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
