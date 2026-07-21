package com.ewos.payroll.domain;

import java.util.List;
import java.util.UUID;

/**
 * Result of running the payroll pre-flight validator against a company + period.
 *
 * <p>{@code blockers} — issues that must be fixed before a run may start (missing compensation,
 * missing bank account, etc.). {@code warnings} — advisory issues that do not stop the run.
 */
public record PayrollValidationReport(List<Issue> blockers, List<Issue> warnings) {

    public boolean isRunnable() {
        return blockers == null || blockers.isEmpty();
    }

    /** One validation finding, keyed by employee and issue code. */
    public record Issue(UUID employeeId, String employeeName, String code, String message) {}
}
