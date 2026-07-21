package com.ewos.payroll.domain;

import com.ewos.payroll.api.dto.reports.RegisterResponse;
import com.ewos.payroll.api.dto.reports.RegisterRowResponse;
import org.springframework.stereotype.Component;

/** CSV writer for payroll-register-style responses. Same RFC-4180 conventions as M4. */
@Component
public final class RegisterCsvExporter {

    private static final String HEADER =
            "payslip_id,payroll_run_id,employee_id,employee_number,employee_name,"
                    + "period_start,period_end,pay_date,currency,basic_effective,lop_days,"
                    + "gross_amount,deductions_amount,net_amount,run_type,status";

    public String export(RegisterResponse response) {
        StringBuilder sb = new StringBuilder(256 + response.rows().size() * 128);
        sb.append(HEADER).append('\n');
        for (RegisterRowResponse r : response.rows()) {
            sb.append(nullSafeId(r.payslipId()))
                    .append(',')
                    .append(nullSafeId(r.payrollRunId()))
                    .append(',')
                    .append(nullSafeId(r.employeeId()))
                    .append(',')
                    .append(csv(nullSafe(r.employeeNumber())))
                    .append(',')
                    .append(csv(nullSafe(r.employeeName())))
                    .append(',')
                    .append(nullSafe(r.periodStart() == null ? "" : r.periodStart().toString()))
                    .append(',')
                    .append(nullSafe(r.periodEnd() == null ? "" : r.periodEnd().toString()))
                    .append(',')
                    .append(nullSafe(r.payDate() == null ? "" : r.payDate().toString()))
                    .append(',')
                    .append(csv(nullSafe(r.currency())))
                    .append(',')
                    .append(
                            nullSafe(
                                    r.basicEffective() == null
                                            ? "0"
                                            : r.basicEffective().toPlainString()))
                    .append(',')
                    .append(nullSafe(r.lopDays() == null ? "0" : r.lopDays().toPlainString()))
                    .append(',')
                    .append(
                            nullSafe(
                                    r.grossAmount() == null
                                            ? "0"
                                            : r.grossAmount().toPlainString()))
                    .append(',')
                    .append(
                            nullSafe(
                                    r.deductionsAmount() == null
                                            ? "0"
                                            : r.deductionsAmount().toPlainString()))
                    .append(',')
                    .append(nullSafe(r.netAmount() == null ? "0" : r.netAmount().toPlainString()))
                    .append(',')
                    .append(csv(nullSafe(r.runType())))
                    .append(',')
                    .append(csv(nullSafe(r.status())))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String nullSafeId(java.util.UUID id) {
        return id == null ? "" : id.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        boolean needsQuote =
                v.indexOf(',') >= 0
                        || v.indexOf('"') >= 0
                        || v.indexOf('\n') >= 0
                        || v.indexOf('\r') >= 0;
        return needsQuote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
