package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.reports.CostCentreReportRowResponse;
import com.ewos.payroll.api.dto.reports.ExecutiveDashboardResponse;
import com.ewos.payroll.api.dto.reports.PayrollDashboardResponse;
import com.ewos.payroll.api.dto.reports.RegisterResponse;
import com.ewos.payroll.api.dto.reports.VarianceReportResponse;
import com.ewos.payroll.application.PayrollReportsService;
import com.ewos.payroll.domain.RegisterCsvExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/reports")
@Tag(
        name = "Payroll Reports",
        description = "Registers, variance reports, cost-centre reports, dashboards")
public class PayrollReportsController {

    private final PayrollReportsService reports;
    private final RegisterCsvExporter csv;

    public PayrollReportsController(PayrollReportsService reports, RegisterCsvExporter csv) {
        this.reports = reports;
        this.csv = csv;
    }

    @GetMapping("/salary-register")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Salary register for a single payroll run")
    public RegisterResponse salaryRegister(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID runId) {
        return reports.salaryRegisterForRun(tenantId, companyId, runId);
    }

    @GetMapping(value = "/salary-register.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Salary register as CSV")
    public ResponseEntity<String> salaryRegisterCsv(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID runId) {
        String body = csv.export(reports.salaryRegisterForRun(tenantId, companyId, runId));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"salary-register-" + runId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @GetMapping("/payroll-register")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Payroll register for a whole payroll period")
    public RegisterResponse payrollRegister(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID periodId) {
        return reports.payrollRegisterForPeriod(tenantId, companyId, periodId);
    }

    @GetMapping("/supplementary-register")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Supplementary run register")
    public RegisterResponse supplementaryRegister(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID runId) {
        return reports.supplementaryRegister(tenantId, companyId, runId);
    }

    @GetMapping("/final-settlement-register")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Full & Final settlement register")
    public RegisterResponse finalSettlementRegister(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID runId) {
        return reports.finalSettlementRegister(tenantId, companyId, runId);
    }

    @GetMapping("/variance/net")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Employee-level NET variance between two runs")
    public VarianceReportResponse netVariance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID currentRunId,
            @RequestParam UUID previousRunId) {
        return reports.netVariance(tenantId, companyId, currentRunId, previousRunId);
    }

    @GetMapping("/variance/gross")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Employee-level GROSS variance between two runs")
    public VarianceReportResponse grossVariance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID currentRunId,
            @RequestParam UUID previousRunId) {
        return reports.grossVariance(tenantId, companyId, currentRunId, previousRunId);
    }

    @GetMapping("/variance/deductions")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Employee-level DEDUCTIONS variance between two runs")
    public VarianceReportResponse deductionsVariance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID currentRunId,
            @RequestParam UUID previousRunId) {
        return reports.deductionsVariance(tenantId, companyId, currentRunId, previousRunId);
    }

    @GetMapping("/cost-centre")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Cost centre roll-up of journal totals for a run")
    public List<CostCentreReportRowResponse> costCentreReport(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID runId) {
        return reports.costCentreReport(tenantId, runId);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Payroll operator dashboard KPIs")
    public PayrollDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return reports.dashboard(tenantId, companyId);
    }

    @GetMapping("/executive-dashboard")
    @PreAuthorize("hasAuthority('PAYROLL_REPORTS')")
    @Operation(summary = "Executive dashboard: aggregate payroll KPIs")
    public ExecutiveDashboardResponse executiveDashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return reports.executiveDashboard(tenantId, companyId);
    }
}
