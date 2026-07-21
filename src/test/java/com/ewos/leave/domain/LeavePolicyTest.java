package com.ewos.leave.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LeavePolicyTest {

    private final LeavePolicy policy = new LeavePolicy();

    @Test
    void requestInThePastRejected() {
        LeaveType type = paidType(0);
        LeaveRequest r = request(type, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
        assertThatThrownBy(() -> policy.assertRequestable(r, LocalDate.of(2026, 6, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("past");
    }

    @Test
    void minNoticeEnforced() {
        LeaveType type = paidType(5);
        LeaveRequest r = request(type, LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 5));
        assertThatThrownBy(() -> policy.assertRequestable(r, LocalDate.of(2026, 6, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("5 days notice");
    }

    @Test
    void minNoticeMetPassesRequestable() {
        LeaveType type = paidType(5);
        LeaveRequest r = request(type, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 12));
        assertThatCode(() -> policy.assertRequestable(r, LocalDate.of(2026, 6, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void unpaidLeaveSkipsBalanceCheck() {
        LeaveType type = paidType(0);
        type.setPaid(false);
        LeaveRequest r = request(type, LocalDate.now(), LocalDate.now());
        r.setDaysRequested(new BigDecimal("100"));
        // Zero balance should still pass because the leave is unpaid.
        assertThatCode(() -> policy.assertSufficientBalance(r, BigDecimal.ZERO))
                .doesNotThrowAnyException();
    }

    @Test
    void insufficientBalanceRejected() {
        LeaveType type = paidType(0);
        LeaveRequest r = request(type, LocalDate.now(), LocalDate.now());
        r.setDaysRequested(new BigDecimal("5"));
        assertThatThrownBy(() -> policy.assertSufficientBalance(r, new BigDecimal("2")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void onlyDraftSubmittable() {
        LeaveRequest r = request(paidType(0), LocalDate.now(), LocalDate.now());
        r.setStatus(LeaveRequestStatus.SUBMITTED);
        assertThatThrownBy(() -> policy.assertSubmittable(r)).isInstanceOf(ApiException.class);
    }

    @Test
    void onlySubmittedDecidable() {
        LeaveRequest r = request(paidType(0), LocalDate.now(), LocalDate.now());
        r.setStatus(LeaveRequestStatus.DRAFT);
        assertThatThrownBy(() -> policy.assertDecidable(r)).isInstanceOf(ApiException.class);
    }

    @Test
    void approvedCancelableOnlyByAdmin() {
        LeaveRequest r = request(paidType(0), LocalDate.now(), LocalDate.now());
        r.setStatus(LeaveRequestStatus.APPROVED);
        assertThatThrownBy(() -> policy.assertCancelable(r, false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("administrator");
        assertThatCode(() -> policy.assertCancelable(r, true)).doesNotThrowAnyException();
    }

    @Test
    void alreadyCancelledCannotCancel() {
        LeaveRequest r = request(paidType(0), LocalDate.now(), LocalDate.now());
        r.setStatus(LeaveRequestStatus.CANCELLED);
        assertThatThrownBy(() -> policy.assertCancelable(r, true)).isInstanceOf(ApiException.class);
    }

    private static LeaveType paidType(int noticeDays) {
        LeaveType t = new LeaveType();
        t.setCode("VACATION");
        t.setName("Vacation");
        t.setPaid(true);
        t.setMinNoticeDays(noticeDays);
        return t;
    }

    private static LeaveRequest request(LeaveType type, LocalDate start, LocalDate end) {
        LeaveRequest r = new LeaveRequest();
        r.setLeaveType(type);
        r.setStartDate(start);
        r.setEndDate(end);
        r.setDaysRequested(BigDecimal.ONE);
        r.setStatus(LeaveRequestStatus.DRAFT);
        return r;
    }
}
