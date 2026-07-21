package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayrollValidationReportResponse;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollValidationReport;
import com.ewos.payroll.domain.PayrollValidator;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the pre-flight validator against the set of employees that would be processed by a payroll
 * run over the given period. Callers use this to preview blockers before starting the run.
 */
@Service
@Transactional(readOnly = true)
public class PayrollValidationService {

    private final PayrollPeriodService periods;
    private final EmployeeCompensationService compensations;
    private final PayrollValidator validator;
    private final PayrollMapper mapper;

    public PayrollValidationService(
            PayrollPeriodService periods,
            EmployeeCompensationService compensations,
            PayrollValidator validator,
            PayrollMapper mapper) {
        this.periods = periods;
        this.compensations = compensations;
        this.validator = validator;
        this.mapper = mapper;
    }

    public PayrollValidationReportResponse validate(
            UUID tenantId, UUID companyId, UUID payrollPeriodId) {
        PayrollPeriod period = periods.require(tenantId, payrollPeriodId);
        if (!period.getCompanyId().equals(companyId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Period belongs to a different company");
        }
        List<EmployeeCompensation> active = compensations.activeForCompany(tenantId, companyId);
        List<Employee> employees =
                active.stream()
                        .map(EmployeeCompensation::getEmployee)
                        .filter(e -> e != null)
                        .toList();
        PayrollValidationReport report = validator.validate(tenantId, employees);
        return mapper.toResponse(tenantId, companyId, payrollPeriodId, employees.size(), report);
    }
}
