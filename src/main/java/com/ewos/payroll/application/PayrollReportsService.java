package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.reports.CostCentreReportRowResponse;
import com.ewos.payroll.api.dto.reports.ExecutiveDashboardResponse;
import com.ewos.payroll.api.dto.reports.PayrollDashboardResponse;
import com.ewos.payroll.api.dto.reports.RegisterResponse;
import com.ewos.payroll.api.dto.reports.RegisterRowResponse;
import com.ewos.payroll.api.dto.reports.VarianceReportResponse;
import com.ewos.payroll.api.dto.reports.VarianceRowResponse;
import com.ewos.payroll.domain.PayrollJournalLine;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunType;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.PayrollJournalRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only projections over the payroll payload. Serves salary register, payroll register, bank
 * register, F&F register, supplementary register, variance reports, cost-centre roll-ups, and
 * dashboards. Format-agnostic — the controller / exporter turn responses into JSON / CSV / etc.
 */
@Service
@Transactional(readOnly = true)
public class PayrollReportsService {

    private static final int PCT_SCALE = 4;

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final PayrollJournalRepository journals;

    public PayrollReportsService(
            PayrollRunRepository runs,
            PayslipRepository payslips,
            PayrollJournalRepository journals) {
        this.runs = runs;
        this.payslips = payslips;
        this.journals = journals;
    }

    // ---- Registers --------------------------------------------------------

    public RegisterResponse salaryRegisterForRun(UUID tenantId, UUID companyId, UUID runId) {
        PayrollRun run = requireRun(tenantId, runId, companyId);
        return buildRegister(
                "SALARY_REGISTER",
                tenantId,
                companyId,
                payslips.findAllForRun(tenantId, run.getId()));
    }

    public RegisterResponse payrollRegisterForPeriod(UUID tenantId, UUID companyId, UUID periodId) {
        List<Payslip> all = new ArrayList<>();
        for (PayrollRun r : runs.findAllForPeriod(tenantId, periodId)) {
            if (r.getCompanyId().equals(companyId)) {
                all.addAll(payslips.findAllForRun(tenantId, r.getId()));
            }
        }
        return buildRegister("PAYROLL_REGISTER", tenantId, companyId, all);
    }

    public RegisterResponse supplementaryRegister(UUID tenantId, UUID companyId, UUID runId) {
        PayrollRun run = requireRun(tenantId, runId, companyId);
        if (run.getRunType() != PayrollRunType.SUPPLEMENTARY) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Run is not a SUPPLEMENTARY run");
        }
        return buildRegister(
                "SUPPLEMENTARY_REGISTER",
                tenantId,
                companyId,
                payslips.findAllForRun(tenantId, run.getId()));
    }

    public RegisterResponse finalSettlementRegister(UUID tenantId, UUID companyId, UUID runId) {
        PayrollRun run = requireRun(tenantId, runId, companyId);
        if (run.getRunType() != PayrollRunType.FINAL_SETTLEMENT) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Run is not a FINAL_SETTLEMENT run");
        }
        return buildRegister(
                "FINAL_SETTLEMENT_REGISTER",
                tenantId,
                companyId,
                payslips.findAllForRun(tenantId, run.getId()));
    }

    // ---- Variance reports -------------------------------------------------

    public VarianceReportResponse netVariance(
            UUID tenantId, UUID companyId, UUID currentRunId, UUID previousRunId) {
        return variance(
                "NET", tenantId, companyId, currentRunId, previousRunId, Payslip::getNetAmount);
    }

    public VarianceReportResponse grossVariance(
            UUID tenantId, UUID companyId, UUID currentRunId, UUID previousRunId) {
        return variance(
                "GROSS", tenantId, companyId, currentRunId, previousRunId, Payslip::getGrossAmount);
    }

    public VarianceReportResponse deductionsVariance(
            UUID tenantId, UUID companyId, UUID currentRunId, UUID previousRunId) {
        return variance(
                "DEDUCTIONS",
                tenantId,
                companyId,
                currentRunId,
                previousRunId,
                Payslip::getDeductionsAmount);
    }

    private VarianceReportResponse variance(
            String metric,
            UUID tenantId,
            UUID companyId,
            UUID currentRunId,
            UUID previousRunId,
            java.util.function.Function<Payslip, BigDecimal> extractor) {
        List<Payslip> current = payslips.findAllForRun(tenantId, currentRunId);
        List<Payslip> previous = payslips.findAllForRun(tenantId, previousRunId);
        current.forEach(p -> requireCompany(p, companyId));
        previous.forEach(p -> requireCompany(p, companyId));
        Map<UUID, Payslip> prevByEmp = new HashMap<>();
        for (Payslip p : previous) {
            if (p.getEmployee() != null) {
                prevByEmp.put(p.getEmployee().getId(), p);
            }
        }
        Set<UUID> allEmps = new HashSet<>(prevByEmp.keySet());
        for (Payslip p : current) {
            if (p.getEmployee() != null) {
                allEmps.add(p.getEmployee().getId());
            }
        }
        Map<UUID, Payslip> currByEmp = new HashMap<>();
        for (Payslip p : current) {
            if (p.getEmployee() != null) {
                currByEmp.put(p.getEmployee().getId(), p);
            }
        }
        List<VarianceRowResponse> rows = new ArrayList<>(allEmps.size());
        for (UUID empId : allEmps) {
            Payslip currP = currByEmp.get(empId);
            Payslip prevP = prevByEmp.get(empId);
            BigDecimal c = currP != null ? extractor.apply(currP) : BigDecimal.ZERO;
            BigDecimal pv = prevP != null ? extractor.apply(prevP) : BigDecimal.ZERO;
            BigDecimal delta = c.subtract(pv);
            BigDecimal pct =
                    pv.signum() == 0
                            ? BigDecimal.ZERO
                            : delta.multiply(new BigDecimal("100"))
                                    .divide(pv, PCT_SCALE, RoundingMode.HALF_UP);
            Payslip nameSource = currP != null ? currP : prevP;
            rows.add(
                    new VarianceRowResponse(
                            empId,
                            nameSource != null ? nameSource.getEmployeeNumberSnapshot() : "",
                            nameSource != null ? nameSource.getEmployeeNameSnapshot() : "",
                            c,
                            pv,
                            delta,
                            pct));
        }
        return new VarianceReportResponse(
                metric,
                currentRunId,
                previousRunId,
                currByEmp.size(),
                prevByEmp.size(),
                currByEmp.size() - prevByEmp.size(),
                rows);
    }

    // ---- Cost centre report ----------------------------------------------

    public List<CostCentreReportRowResponse> costCentreReport(UUID tenantId, UUID runId) {
        Map<String, BigDecimal[]> agg = new TreeMap<>();
        journals.findAllForRun(tenantId, runId)
                .forEach(
                        j -> {
                            for (PayrollJournalLine line : j.getLines()) {
                                String key =
                                        (line.getCostCentreCode() == null
                                                        ? "-"
                                                        : line.getCostCentreCode())
                                                + "|"
                                                + line.getGlAccountCode();
                                BigDecimal[] pair =
                                        agg.computeIfAbsent(
                                                key,
                                                k ->
                                                        new BigDecimal[] {
                                                            BigDecimal.ZERO, BigDecimal.ZERO
                                                        });
                                pair[0] = pair[0].add(line.getDebitAmount());
                                pair[1] = pair[1].add(line.getCreditAmount());
                            }
                        });
        List<CostCentreReportRowResponse> out = new ArrayList<>(agg.size());
        for (Map.Entry<String, BigDecimal[]> e : agg.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            out.add(
                    new CostCentreReportRowResponse(
                            parts[0], parts[1], e.getValue()[0], e.getValue()[1]));
        }
        return out;
    }

    // ---- Dashboards ------------------------------------------------------

    public PayrollDashboardResponse dashboard(UUID tenantId, UUID companyId) {
        return new PayrollDashboardResponse(
                tenantId, companyId, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public ExecutiveDashboardResponse executiveDashboard(UUID tenantId, UUID companyId) {
        return new ExecutiveDashboardResponse(
                tenantId,
                companyId,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0);
    }

    // ---- helpers ---------------------------------------------------------

    private RegisterResponse buildRegister(
            String reportCode, UUID tenantId, UUID companyId, List<Payslip> slips) {
        List<RegisterRowResponse> rows = new ArrayList<>(slips.size());
        BigDecimal totalG = BigDecimal.ZERO;
        BigDecimal totalD = BigDecimal.ZERO;
        BigDecimal totalN = BigDecimal.ZERO;
        for (Payslip p : slips) {
            requireCompany(p, companyId);
            rows.add(
                    new RegisterRowResponse(
                            p.getId(),
                            p.getPayrollRun() != null ? p.getPayrollRun().getId() : null,
                            p.getEmployee() != null ? p.getEmployee().getId() : null,
                            p.getEmployeeNumberSnapshot(),
                            p.getEmployeeNameSnapshot(),
                            p.getPeriodStart(),
                            p.getPeriodEnd(),
                            p.getPayDate(),
                            p.getCurrency(),
                            p.getBasicEffective(),
                            p.getLopDays(),
                            p.getGrossAmount(),
                            p.getDeductionsAmount(),
                            p.getNetAmount(),
                            p.getPayrollRun() != null && p.getPayrollRun().getRunType() != null
                                    ? p.getPayrollRun().getRunType().name()
                                    : "",
                            p.getStatus().name()));
            totalG = totalG.add(p.getGrossAmount());
            totalD = totalD.add(p.getDeductionsAmount());
            totalN = totalN.add(p.getNetAmount());
        }
        return new RegisterResponse(
                reportCode, tenantId, companyId, rows.size(), totalG, totalD, totalN, rows);
    }

    private PayrollRun requireRun(UUID tenantId, UUID runId, UUID companyId) {
        PayrollRun run =
                runs.findByIdAndTenantId(runId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Payroll run not found"));
        if (!run.getCompanyId().equals(companyId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Run belongs to a different company");
        }
        return run;
    }

    private static void requireCompany(Payslip p, UUID companyId) {
        if (!p.getCompanyId().equals(companyId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Payslip belongs to a different company");
        }
    }
}
