package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only projections over the immutable payslip snapshots. */
@Service
@Transactional(readOnly = true)
public class PayslipService {

    private final PayslipRepository repository;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;

    public PayslipService(
            PayslipRepository repository, PayrollMapper mapper, ClientAccessGuard guard) {
        this.repository = repository;
        this.mapper = mapper;
        this.guard = guard;
    }

    public PayslipResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(requireForAdmin(tenantId, id));
    }

    /** Same access check as {@link #getById}, returning the entity for callers that need it. */
    public Payslip requireForAdmin(UUID tenantId, UUID id) {
        Payslip payslip =
                repository
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Payslip not found"));
        guard.requireAccessForCompany(payslip.getCompanyId());
        return payslip;
    }

    public List<PayslipResponse> forRun(UUID tenantId, UUID runId) {
        return entitiesForRun(tenantId, runId).stream().map(mapper::toResponse).toList();
    }

    /** Same access check as {@link #forRun}, returning entities for callers that need them. */
    public List<Payslip> entitiesForRun(UUID tenantId, UUID runId) {
        List<Payslip> slips = repository.findAllForRun(tenantId, runId);
        guard.requireAccessForCompanies(slips.stream().map(Payslip::getCompanyId).toList());
        return slips;
    }

    public List<PayslipResponse> forEmployee(UUID tenantId, UUID employeeId) {
        List<Payslip> slips = repository.findAllForEmployee(tenantId, employeeId);
        guard.requireAccessForCompanies(slips.stream().map(Payslip::getCompanyId).toList());
        return slips.stream().map(mapper::toResponse).toList();
    }

    /**
     * Self-service payslip detail: an ownership check (is this the caller's own payslip?) rather
     * than {@link ClientAccessGuard}'s company-access check — an employee reading their own record
     * needs no company-level payroll authority at all.
     */
    public PayslipResponse getOwnPayslip(UUID tenantId, UUID employeeId, UUID id) {
        return mapper.toResponse(requireOwn(tenantId, employeeId, id));
    }

    /** Same ownership check as {@link #getOwnPayslip}, returning the entity for PDF generation. */
    public Payslip requireOwn(UUID tenantId, UUID employeeId, UUID id) {
        Payslip payslip =
                repository
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Payslip not found"));
        if (payslip.getEmployee() == null || !employeeId.equals(payslip.getEmployee().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payslip not found");
        }
        return payslip;
    }
}
