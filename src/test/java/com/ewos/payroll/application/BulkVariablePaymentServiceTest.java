package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.BulkVariablePaymentReportResponse;
import com.ewos.payroll.api.dto.BulkVariablePaymentRow;
import com.ewos.payroll.api.dto.BulkVariablePaymentUploadRequest;
import com.ewos.payroll.domain.BulkVariablePaymentBatch;
import com.ewos.payroll.domain.BulkVariablePaymentBatchStatus;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.infrastructure.persistence.BulkVariablePaymentBatchRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkVariablePaymentServiceTest {

    @Mock EmployeeRepository employees;
    @Mock PayrollArrearRepository arrearRepository;
    @Mock BulkVariablePaymentBatchRepository batches;
    @Mock ClientAccessGuard guard;
    private final PayrollMapper mapper = new PayrollMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private BulkVariablePaymentService service;
    private PayrollArrearService arrearService;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        arrearService = new PayrollArrearService(arrearRepository, employees, mapper, guard);
        service =
                new BulkVariablePaymentService(employees, arrearService, batches, guard, validator);

        org.mockito.Mockito.lenient()
                .when(arrearRepository.save(any(PayrollArrear.class)))
                .thenAnswer(
                        inv -> {
                            PayrollArrear a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId(UUID.randomUUID());
                            }
                            return a;
                        });
        org.mockito.Mockito.lenient()
                .when(batches.save(any(BulkVariablePaymentBatch.class)))
                .thenAnswer(
                        inv -> {
                            BulkVariablePaymentBatch b = inv.getArgument(0);
                            if (b.getId() == null) {
                                b.setId(UUID.randomUUID());
                            }
                            return b;
                        });
    }

    private Employee employee(String number) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setCompanyId(companyId);
        e.setEmployeeNumber(number);
        return e;
    }

    private BulkVariablePaymentUploadRequest requestWith(List<BulkVariablePaymentRow> rows) {
        return new BulkVariablePaymentUploadRequest(tenantId, companyId, "bonus_q2.csv", rows);
    }

    @Test
    void previewFlagsAnUnknownEmployeeNumberWithoutCreatingAnything() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E999"))
                .thenReturn(Optional.empty());

        BulkVariablePaymentReportResponse report =
                service.preview(
                        requestWith(
                                List.of(
                                        new BulkVariablePaymentRow(
                                                "E999",
                                                "BONUS_Q2",
                                                "Q2 bonus",
                                                new BigDecimal("5000"),
                                                PayComponentKind.EARNING,
                                                null,
                                                null))));

        assertThat(report.batchId()).isNull();
        assertThat(report.status()).isNull();
        assertThat(report.totalRows()).isEqualTo(1);
        assertThat(report.errorRows()).isEqualTo(1);
        assertThat(report.rows().get(0).valid()).isFalse();
        assertThat(report.rows().get(0).errors()).anyMatch(e -> e.contains("E999"));

        verify(arrearRepository, never()).save(any());
        verify(batches, never()).save(any());
    }

    @Test
    void previewFlagsAnInvalidReasonCodeFormat() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E100"))
                .thenReturn(Optional.of(employee("E100")));

        BulkVariablePaymentReportResponse report =
                service.preview(
                        requestWith(
                                List.of(
                                        new BulkVariablePaymentRow(
                                                "E100",
                                                "not a valid code!",
                                                "Bad reason code",
                                                new BigDecimal("1000"),
                                                PayComponentKind.EARNING,
                                                null,
                                                null))));

        assertThat(report.rows().get(0).valid()).isFalse();
        assertThat(report.rows().get(0).errors()).anyMatch(e -> e.contains("reasonCode"));
    }

    @Test
    void commitCreatesOneArrearPerRowAndACommittedBatchWhenEveryRowIsValid() {
        Employee e100 = employee("E100");
        Employee e101 = employee("E101");
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E100"))
                .thenReturn(Optional.of(e100));
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E101"))
                .thenReturn(Optional.of(e101));
        org.mockito.Mockito.lenient()
                .when(employees.findByIdAndTenantId(e100.getId(), tenantId))
                .thenReturn(Optional.of(e100));
        org.mockito.Mockito.lenient()
                .when(employees.findByIdAndTenantId(e101.getId(), tenantId))
                .thenReturn(Optional.of(e101));

        BulkVariablePaymentReportResponse report =
                service.commit(
                        requestWith(
                                List.of(
                                        new BulkVariablePaymentRow(
                                                "E100",
                                                "BONUS_Q2",
                                                "Q2 bonus",
                                                new BigDecimal("5000"),
                                                PayComponentKind.EARNING,
                                                null,
                                                null),
                                        new BulkVariablePaymentRow(
                                                "E101",
                                                "INCENTIVE_SALES",
                                                "Sales incentive",
                                                new BigDecimal("3000"),
                                                PayComponentKind.EARNING,
                                                null,
                                                null))));

        assertThat(report.batchId()).isNotNull();
        assertThat(report.status()).isEqualTo(BulkVariablePaymentBatchStatus.COMMITTED);
        assertThat(report.totalRows()).isEqualTo(2);
        assertThat(report.validRows()).isEqualTo(2);
        assertThat(report.errorRows()).isEqualTo(0);
        assertThat(report.rows()).hasSize(2);
        assertThat(report.rows()).allMatch(r -> r.valid() && r.createdArrearId() != null);

        org.mockito.ArgumentCaptor<PayrollArrear> captor =
                org.mockito.ArgumentCaptor.forClass(PayrollArrear.class);
        verify(arrearRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allMatch(a -> a.getBulkUploadBatchId().equals(report.batchId()));
    }

    @Test
    void commitWritesOnlyARejectedBatchAndNoArrearsWhenAnyRowIsInvalid() {
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E100"))
                .thenReturn(Optional.of(employee("E100")));
        when(employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                        tenantId, companyId, "E999"))
                .thenReturn(Optional.empty());

        BulkVariablePaymentReportResponse report =
                service.commit(
                        requestWith(
                                List.of(
                                        new BulkVariablePaymentRow(
                                                "E100",
                                                "BONUS_Q2",
                                                "Q2 bonus",
                                                new BigDecimal("5000"),
                                                PayComponentKind.EARNING,
                                                null,
                                                null),
                                        new BulkVariablePaymentRow(
                                                "E999",
                                                "BONUS_Q2",
                                                "Unknown employee",
                                                new BigDecimal("5000"),
                                                PayComponentKind.EARNING,
                                                null,
                                                null))));

        assertThat(report.status()).isEqualTo(BulkVariablePaymentBatchStatus.REJECTED);
        assertThat(report.errorRows()).isEqualTo(1);
        assertThat(report.rows()).hasSize(2);
        assertThat(report.rows()).anyMatch(r -> !r.valid() && "E999".equals(r.employeeNumber()));
        assertThat(report.rows()).noneMatch(r -> r.createdArrearId() != null);

        verify(arrearRepository, never()).save(any());

        org.mockito.ArgumentCaptor<BulkVariablePaymentBatch> batchCaptor =
                org.mockito.ArgumentCaptor.forClass(BulkVariablePaymentBatch.class);
        verify(batches).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus())
                .isEqualTo(BulkVariablePaymentBatchStatus.REJECTED);
        assertThat(batchCaptor.getValue().getErrorRows()).isEqualTo(1);
    }
}
