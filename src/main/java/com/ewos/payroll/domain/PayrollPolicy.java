package com.ewos.payroll.domain;

import com.ewos.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Framework-neutral rule enforcer for payroll state transitions. Every mutation goes through the
 * matching {@code assert...} helper so the guard set stays in one place and is unit-testable
 * without Spring context.
 */
@Component
public final class PayrollPolicy {

    /** Period must be OPEN to accept edits (dates, code, name, frequency). */
    public void assertEditable(PayrollPeriod period) {
        if (period.getStatus() != PayrollPeriodStatus.OPEN) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Payroll period is "
                            + period.getStatus()
                            + "; only OPEN periods can be edited");
        }
    }

    /** OPEN → LOCKED. */
    public void assertLockable(PayrollPeriod period) {
        if (period.getStatus() != PayrollPeriodStatus.OPEN) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only OPEN periods can be locked; current status is " + period.getStatus());
        }
    }

    /** LOCKED → CLOSED. Runs against this period must already be FINALIZED. */
    public void assertClosable(PayrollPeriod period) {
        if (period.getStatus() != PayrollPeriodStatus.LOCKED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only LOCKED periods can be closed; current status is " + period.getStatus());
        }
    }

    /** A payroll run may only start against a LOCKED period. */
    public void assertRunnable(PayrollPeriod period) {
        if (period.getStatus() != PayrollPeriodStatus.LOCKED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Payroll runs require the period to be LOCKED; current status is "
                            + period.getStatus());
        }
    }

    /** PENDING → PROCESSING. */
    public void assertStartable(PayrollRun run) {
        if (run.getStatus() != PayrollRunStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Payroll run must be PENDING to start; current status is " + run.getStatus());
        }
    }

    /** COMPLETED → FINALIZED. */
    public void assertFinalizable(PayrollRun run) {
        if (run.getStatus() != PayrollRunStatus.COMPLETED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only COMPLETED payroll runs can be finalized; current status is "
                            + run.getStatus());
        }
    }

    /** Non-terminal to FAILED. */
    public void assertFailable(PayrollRun run) {
        if (run.getStatus() == PayrollRunStatus.FINALIZED
                || run.getStatus() == PayrollRunStatus.FROZEN
                || run.getStatus() == PayrollRunStatus.FAILED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Payroll run is already " + run.getStatus() + "; cannot mark FAILED");
        }
    }

    /** FINALIZED → FROZEN. Once frozen no supplementary run may alter the payslips. */
    public void assertFreezable(PayrollRun run) {
        if (run.getStatus() != PayrollRunStatus.FINALIZED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only FINALIZED payroll runs can be frozen; current status is "
                            + run.getStatus());
        }
    }
}
