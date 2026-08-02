package com.ewos.payroll.api.dto;

/**
 * Employee self-service payroll dashboard: current salary summary, latest payslip snapshot, and the
 * current fiscal year's tax declaration (YTD summary) in one view. Every field reuses an existing
 * response type — this is purely an aggregation, not a new data model.
 */
public record PayrollDashboardResponse(
        String fiscalYear,
        EmployeeCompensationResponse currentCompensation,
        PayslipResponse latestPayslip,
        EmployeeTaxDeclarationResponse currentTaxDeclaration) {}
