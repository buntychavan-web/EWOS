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
        Payslip payslip =
                repository
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Payslip not found"));
        guard.requireAccessForCompany(payslip.getCompanyId());
        return mapper.toResponse(payslip);
    }

    public List<PayslipResponse> forRun(UUID tenantId, UUID runId) {
        List<Payslip> slips = repository.findAllForRun(tenantId, runId);
        guard.requireAccessForCompanies(slips.stream().map(Payslip::getCompanyId).toList());
        return slips.stream().map(mapper::toResponse).toList();
    }

    public List<PayslipResponse> forEmployee(UUID tenantId, UUID employeeId) {
        List<Payslip> slips = repository.findAllForEmployee(tenantId, employeeId);
        guard.requireAccessForCompanies(slips.stream().map(Payslip::getCompanyId).toList());
        return slips.stream().map(mapper::toResponse).toList();
    }
}
