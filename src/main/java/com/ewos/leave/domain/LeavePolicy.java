package com.ewos.leave.domain;

import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Rule enforcer for leave requests. Framework-neutral.
 *
 * <ul>
 *   <li>Request dates must not lie in the past for a newly-submitted request.
 *   <li>{@code minNoticeDays} on the leave type is respected: request must be filed at least that
 *       many calendar days before {@code startDate}.
 *   <li>Available balance must cover the requested days when the leave type is paid.
 *   <li>Only DRAFT requests can be submitted; only SUBMITTED requests can be approved / rejected;
 *       APPROVED requests cannot be cancelled by employees — admin only.
 * </ul>
 */
@Component
public final class LeavePolicy {

    public void assertRequestable(LeaveRequest request, LocalDate today) {
        if (request.getStartDate().isBefore(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "startDate cannot be in the past");
        }
        int noticeDays = request.getLeaveType().getMinNoticeDays();
        if (noticeDays > 0) {
            long actualNotice = ChronoUnit.DAYS.between(today, request.getStartDate());
            if (actualNotice < noticeDays) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Leave type '"
                                + request.getLeaveType().getCode()
                                + "' requires "
                                + noticeDays
                                + " days notice; only "
                                + actualNotice
                                + " provided");
            }
        }
    }

    public void assertSufficientBalance(LeaveRequest request, BigDecimal availableDays) {
        if (!request.getLeaveType().isPaid()) {
            return;
        }
        if (availableDays.compareTo(request.getDaysRequested()) < 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Insufficient leave balance: requested "
                            + request.getDaysRequested()
                            + " day(s), available "
                            + availableDays);
        }
    }

    public void assertSubmittable(LeaveRequest request) {
        if (request.getStatus() != LeaveRequestStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only DRAFT requests can be submitted (current: " + request.getStatus() + ")");
        }
    }

    public void assertDecidable(LeaveRequest request) {
        if (request.getStatus() != LeaveRequestStatus.SUBMITTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only SUBMITTED requests can be decided (current: "
                            + request.getStatus()
                            + ")");
        }
    }

    public void assertCancelable(LeaveRequest request, boolean actorIsAdmin) {
        if (request.getStatus() == LeaveRequestStatus.CANCELLED
                || request.getStatus() == LeaveRequestStatus.REJECTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Request is already " + request.getStatus() + "; cannot cancel");
        }
        if (request.getStatus() == LeaveRequestStatus.APPROVED && !actorIsAdmin) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Approved leave can only be cancelled by an administrator");
        }
    }
}
