package com.ewos.payroll.application;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only projections over the immutable payslip snapshots.
 *
 * <p>Every admin-side lookup here is additionally row-level-owned (Codex CTO audit follow-up):
 * {@code PAYROLL_READ} alone is not treated as blanket "see any employee's payslip" — that requires
 * {@code PAYROLL_ADMIN} or {@code PAYROLL_RUN} (the authorities genuinely held by
 * payroll-operations staff). A caller with only {@code PAYROLL_READ} and a linked employee record
 * is restricted to their own payslips, so granting {@code PAYROLL_READ} more broadly in the future
 * — to a manager role, for instance — can never leak another employee's payslip by accident.
 * Callers with {@code PAYROLL_READ} and no linked employee record (the normal shape for a system/HR
 * admin account) are unaffected, matching today's behavior exactly.
 */
@Service
@Transactional(readOnly = true)
public class PayslipService {

    private final PayslipRepository repository;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;
    private final EmployeeContext employeeContext;

    public PayslipService(
            PayslipRepository repository,
            PayrollMapper mapper,
            ClientAccessGuard guard,
            EmployeeContext employeeContext) {
        this.repository = repository;
        this.mapper = mapper;
        this.guard = guard;
        this.employeeContext = employeeContext;
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
        requireElevatedOrOwn(payslip.getEmployee().getId());
        return payslip;
    }

    public List<PayslipResponse> forRun(UUID tenantId, UUID runId) {
        return entitiesForRun(tenantId, runId).stream().map(mapper::toResponse).toList();
    }

    /** Same access check as {@link #forRun}, returning entities for callers that need them. */
    public List<Payslip> entitiesForRun(UUID tenantId, UUID runId) {
        List<Payslip> slips = repository.findAllForRun(tenantId, runId);
        guard.requireAccessForCompanies(slips.stream().map(Payslip::getCompanyId).toList());
        if (!hasElevatedPayrollAccess()) {
            UUID callerEmployeeId = employeeContext.currentEmployeeId().orElse(null);
            if (callerEmployeeId != null) {
                slips =
                        slips.stream()
                                .filter(s -> callerEmployeeId.equals(s.getEmployee().getId()))
                                .toList();
            }
        }
        return slips;
    }

    public List<PayslipResponse> forEmployee(UUID tenantId, UUID employeeId) {
        requireElevatedOrOwn(employeeId);
        List<Payslip> slips = repository.findAllForEmployee(tenantId, employeeId);
        guard.requireAccessForCompanies(slips.stream().map(Payslip::getCompanyId).toList());
        return slips.stream().map(mapper::toResponse).toList();
    }

    /**
     * Throws {@code 404} (never {@code 403} — matching {@link #requireOwn}'s existing convention of
     * not confirming another employee's payslip even exists) unless the caller holds an elevated
     * payroll-operations authority or {@code employeeId} is the caller's own.
     */
    private void requireElevatedOrOwn(UUID employeeId) {
        if (hasElevatedPayrollAccess()) {
            return;
        }
        UUID callerEmployeeId = employeeContext.currentEmployeeId().orElse(null);
        if (callerEmployeeId != null && !callerEmployeeId.equals(employeeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payslip not found");
        }
    }

    private static boolean hasElevatedPayrollAccess() {
        return hasAuthority("PAYROLL_ADMIN") || hasAuthority("PAYROLL_RUN");
    }

    private static boolean hasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
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
