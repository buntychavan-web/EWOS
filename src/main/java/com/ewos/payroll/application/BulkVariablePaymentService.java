package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.dto.ArrearResponse;
import com.ewos.payroll.api.dto.BulkVariablePaymentReportResponse;
import com.ewos.payroll.api.dto.BulkVariablePaymentRow;
import com.ewos.payroll.api.dto.BulkVariablePaymentRowResult;
import com.ewos.payroll.api.dto.BulkVariablePaymentUploadRequest;
import com.ewos.payroll.api.dto.CreateArrearRequest;
import com.ewos.payroll.domain.BulkVariablePaymentBatch;
import com.ewos.payroll.domain.BulkVariablePaymentBatchStatus;
import com.ewos.payroll.infrastructure.persistence.BulkVariablePaymentBatchRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24K item 2 — bulk upload for Bonus, Incentives, Variable Pay, Arrears, Adjustments, and
 * One-time Payments. Every row becomes a {@link com.ewos.payroll.domain.PayrollArrear} — the
 * existing one-time-payment vehicle {@code PayrollCalculator}/{@code PayrollRunService} already
 * know how to pick up and tax correctly (Sprint 24K §8.3) — created through {@link
 * PayrollArrearService#create(CreateArrearRequest, UUID)} so this service never duplicates that
 * validation/creation logic, only adds the batch-level orchestration around it.
 *
 * <p>A batch is strictly all-or-nothing: {@link #preview} and {@link #commit} both validate every
 * row against the exact same rules {@link CreateArrearRequest}'s bean validation already enforces
 * for the single-row API, plus resolving the row's employee number to a real, active employee in
 * the company. If any row fails, {@link #commit} writes nothing at all — no arrear, no batch header
 * committed — and returns the same row-by-row error report {@link #preview} would, so the caller
 * can fix the file and resubmit rather than having to reconcile a partially-applied batch. A {@code
 * REJECTED} batch header is still recorded for audit even though no arrears were created, so
 * "someone tried to upload N rows and M failed" is never lost.
 */
@Service
@Transactional
public class BulkVariablePaymentService {

    private final EmployeeRepository employees;
    private final PayrollArrearService arrearService;
    private final BulkVariablePaymentBatchRepository batches;
    private final ClientAccessGuard guard;
    private final Validator validator;

    public BulkVariablePaymentService(
            EmployeeRepository employees,
            PayrollArrearService arrearService,
            BulkVariablePaymentBatchRepository batches,
            ClientAccessGuard guard,
            Validator validator) {
        this.employees = employees;
        this.arrearService = arrearService;
        this.batches = batches;
        this.guard = guard;
        this.validator = validator;
    }

    /** Validates every row without persisting anything — for the caller to review before commit. */
    @Transactional(readOnly = true)
    public BulkVariablePaymentReportResponse preview(BulkVariablePaymentUploadRequest request) {
        guard.requireAccessForCompany(request.companyId());
        List<BulkVariablePaymentRowResult> results = validateRows(request);
        int errorCount = (int) results.stream().filter(r -> !r.valid()).count();
        return new BulkVariablePaymentReportResponse(
                null,
                request.tenantId(),
                request.companyId(),
                request.sourceFilename(),
                null,
                results.size(),
                results.size() - errorCount,
                errorCount,
                results);
    }

    /**
     * Validates every row, then either creates one {@link com.ewos.payroll.domain.PayrollArrear}
     * per row and a {@code COMMITTED} batch header (all rows valid), or writes only a {@code
     * REJECTED} batch header and no arrears at all (any row invalid).
     */
    public BulkVariablePaymentReportResponse commit(BulkVariablePaymentUploadRequest request) {
        guard.requireAccessForCompany(request.companyId());
        List<BulkVariablePaymentRowResult> results = validateRows(request);
        int errorCount = (int) results.stream().filter(r -> !r.valid()).count();
        boolean allValid = errorCount == 0;

        BulkVariablePaymentBatch batch = new BulkVariablePaymentBatch();
        batch.setTenantId(request.tenantId());
        batch.setCompanyId(request.companyId());
        batch.setSourceFilename(request.sourceFilename());
        batch.setTotalRows(results.size());
        batch.setValidRows(results.size() - errorCount);
        batch.setErrorRows(errorCount);
        batch.setStatus(
                allValid
                        ? BulkVariablePaymentBatchStatus.COMMITTED
                        : BulkVariablePaymentBatchStatus.REJECTED);
        BulkVariablePaymentBatch savedBatch = batches.save(batch);

        List<BulkVariablePaymentRowResult> finalResults = results;
        if (allValid) {
            finalResults = new ArrayList<>();
            List<BulkVariablePaymentRow> rows = request.rows();
            for (int i = 0; i < rows.size(); i++) {
                BulkVariablePaymentRow row = rows.get(i);
                Employee employee =
                        employees
                                .findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                                        request.tenantId(),
                                        request.companyId(),
                                        row.employeeNumber())
                                .orElseThrow();
                CreateArrearRequest createRequest = toCreateRequest(request, row, employee.getId());
                ArrearResponse created = arrearService.create(createRequest, savedBatch.getId());
                finalResults.add(
                        new BulkVariablePaymentRowResult(
                                i + 1, row.employeeNumber(), true, List.of(), created.id()));
            }
        }

        return new BulkVariablePaymentReportResponse(
                savedBatch.getId(),
                request.tenantId(),
                request.companyId(),
                request.sourceFilename(),
                savedBatch.getStatus(),
                results.size(),
                results.size() - errorCount,
                errorCount,
                finalResults);
    }

    private List<BulkVariablePaymentRowResult> validateRows(
            BulkVariablePaymentUploadRequest request) {
        List<BulkVariablePaymentRowResult> results = new ArrayList<>();
        List<BulkVariablePaymentRow> rows = request.rows();
        for (int i = 0; i < rows.size(); i++) {
            BulkVariablePaymentRow row = rows.get(i);
            List<String> errors = new ArrayList<>();

            if (row.employeeNumber() == null || row.employeeNumber().isBlank()) {
                errors.add("employeeNumber is required");
            } else {
                Optional<Employee> employee =
                        employees.findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                                request.tenantId(), request.companyId(), row.employeeNumber());
                if (employee.isEmpty()) {
                    errors.add(
                            "No employee with number '"
                                    + row.employeeNumber()
                                    + "' found in this company");
                }
            }

            // A placeholder id — never persisted — so the other fields (reasonCode, amount, kind,
            // …) still get bean-validated even when the employee lookup above already failed.
            CreateArrearRequest createRequest = toCreateRequest(request, row, UUID.randomUUID());
            Set<ConstraintViolation<CreateArrearRequest>> violations =
                    validator.validate(createRequest);
            for (ConstraintViolation<CreateArrearRequest> v : violations) {
                errors.add(v.getPropertyPath() + ": " + v.getMessage());
            }

            results.add(
                    new BulkVariablePaymentRowResult(
                            i + 1, row.employeeNumber(), errors.isEmpty(), errors, null));
        }
        return results;
    }

    private static CreateArrearRequest toCreateRequest(
            BulkVariablePaymentUploadRequest request, BulkVariablePaymentRow row, UUID employeeId) {
        return new CreateArrearRequest(
                request.tenantId(),
                request.companyId(),
                employeeId,
                row.reasonCode(),
                row.description(),
                row.amount(),
                row.kind(),
                row.forPeriodStart(),
                row.forPeriodEnd());
    }

    @Transactional(readOnly = true)
    public BulkVariablePaymentReportResponse getBatch(UUID tenantId, UUID batchId) {
        BulkVariablePaymentBatch batch =
                batches.findByIdAndTenantId(batchId, tenantId)
                        .orElseThrow(
                                () ->
                                        new com.ewos.shared.exception.ApiException(
                                                org.springframework.http.HttpStatus.NOT_FOUND,
                                                "Bulk upload batch not found"));
        guard.requireAccessForCompany(batch.getCompanyId());
        return new BulkVariablePaymentReportResponse(
                batch.getId(),
                batch.getTenantId(),
                batch.getCompanyId(),
                batch.getSourceFilename(),
                batch.getStatus(),
                batch.getTotalRows(),
                batch.getValidRows(),
                batch.getErrorRows(),
                List.of());
    }
}
