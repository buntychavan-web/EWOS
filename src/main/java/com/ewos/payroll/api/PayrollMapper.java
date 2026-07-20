package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CompensationLineResponse;
import com.ewos.payroll.api.dto.EmployeeCompensationResponse;
import com.ewos.payroll.api.dto.PayComponentResponse;
import com.ewos.payroll.api.dto.PayrollPeriodResponse;
import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.api.dto.PayslipLineResponse;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.EmployeeCompensationLine;
import com.ewos.payroll.domain.PayComponent;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class PayrollMapper {

    public PayComponentResponse toResponse(PayComponent c) {
        return new PayComponentResponse(
                c.getId(),
                c.getTenantId(),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.getKind(),
                c.getCalculationType(),
                c.getDefaultAmount(),
                c.getDefaultPercentage(),
                c.isTaxable(),
                c.isActive(),
                c.getSortOrder(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getVersionNo());
    }

    public PayrollPeriodResponse toResponse(PayrollPeriod p) {
        return new PayrollPeriodResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getCode(),
                p.getName(),
                p.getFrequency(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.getPayDate(),
                p.getStatus(),
                p.getLockedAt(),
                p.getLockedBy(),
                p.getClosedAt(),
                p.getClosedBy(),
                p.getVersionNo());
    }

    public EmployeeCompensationResponse toResponse(EmployeeCompensation c) {
        List<CompensationLineResponse> lines =
                c.getLines().stream().map(PayrollMapper::toLineResponse).toList();
        return new EmployeeCompensationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getEmployee() != null ? c.getEmployee().getId() : null,
                c.getEffectiveFrom(),
                c.getEffectiveTo(),
                c.getFrequency(),
                c.getBasicSalary(),
                c.getCurrency(),
                c.getNotes(),
                c.isActive(),
                lines,
                c.getVersionNo());
    }

    public PayrollRunResponse toResponse(PayrollRun r) {
        return new PayrollRunResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getPayrollPeriod() != null ? r.getPayrollPeriod().getId() : null,
                r.getStatus(),
                r.getStartedAt(),
                r.getStartedBy(),
                r.getCompletedAt(),
                r.getFinalizedAt(),
                r.getFinalizedBy(),
                r.getFailedAt(),
                r.getFailureReason(),
                r.getEmployeesProcessed(),
                r.getTotalGross(),
                r.getTotalDeductions(),
                r.getTotalNet(),
                r.getVersionNo());
    }

    public PayslipResponse toResponse(Payslip p) {
        List<PayslipLineResponse> lines =
                p.getLines().stream().map(PayrollMapper::toLineResponse).toList();
        return new PayslipResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getPayrollRun() != null ? p.getPayrollRun().getId() : null,
                p.getPayrollPeriod() != null ? p.getPayrollPeriod().getId() : null,
                p.getEmployee() != null ? p.getEmployee().getId() : null,
                p.getEmployeeNumberSnapshot(),
                p.getEmployeeNameSnapshot(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.getPayDate(),
                p.getCurrency(),
                p.getGrossAmount(),
                p.getDeductionsAmount(),
                p.getNetAmount(),
                p.getStatus(),
                p.getFinalizedAt(),
                lines,
                p.getVersionNo());
    }

    private static CompensationLineResponse toLineResponse(EmployeeCompensationLine l) {
        return new CompensationLineResponse(
                l.getId(),
                l.getPayComponent() != null ? l.getPayComponent().getId() : null,
                l.getPayComponent() != null ? l.getPayComponent().getCode() : null,
                l.getAmount(),
                l.getPercentage());
    }

    private static PayslipLineResponse toLineResponse(PayslipLine l) {
        return new PayslipLineResponse(
                l.getId(),
                l.getPayComponent() != null ? l.getPayComponent().getId() : null,
                l.getComponentCodeSnapshot(),
                l.getComponentNameSnapshot(),
                l.getKind(),
                l.getCalculationType(),
                l.getAmount(),
                l.getPercentageApplied(),
                l.getSortOrder());
    }
}
