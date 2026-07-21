package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
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

    public PayslipService(PayslipRepository repository, PayrollMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public PayslipResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(
                repository
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Payslip not found")));
    }

    public List<PayslipResponse> forRun(UUID tenantId, UUID runId) {
        return repository.findAllForRun(tenantId, runId).stream().map(mapper::toResponse).toList();
    }

    public List<PayslipResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return repository.findAllForEmployee(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
