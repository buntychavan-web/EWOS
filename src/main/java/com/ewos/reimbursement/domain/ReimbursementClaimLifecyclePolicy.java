package com.ewos.reimbursement.domain;

import com.ewos.shared.exception.ApiException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Guards state transitions for reimbursement claims. Mirrors {@code ResignationLifecyclePolicy}
 * exactly. No {@code PAID} state exists in Sprint 2 — {@code FINANCE_APPROVED} is terminal here;
 * Sprint 3's payroll linkage introduces a further transition out of it.
 */
@Component
public class ReimbursementClaimLifecyclePolicy {

    private static final Map<ReimbursementClaimStatus, Set<ReimbursementClaimStatus>> ALLOWED =
            Map.of(
                    ReimbursementClaimStatus.DRAFT,
                            EnumSet.of(
                                    ReimbursementClaimStatus.SUBMITTED,
                                    ReimbursementClaimStatus.CANCELLED),
                    ReimbursementClaimStatus.SUBMITTED,
                            EnumSet.of(
                                    ReimbursementClaimStatus.MANAGER_APPROVED,
                                    ReimbursementClaimStatus.MANAGER_REJECTED,
                                    ReimbursementClaimStatus.CANCELLED),
                    ReimbursementClaimStatus.MANAGER_APPROVED,
                            EnumSet.of(
                                    ReimbursementClaimStatus.FINANCE_APPROVED,
                                    ReimbursementClaimStatus.FINANCE_REJECTED),
                    ReimbursementClaimStatus.MANAGER_REJECTED,
                            EnumSet.noneOf(ReimbursementClaimStatus.class),
                    ReimbursementClaimStatus.FINANCE_APPROVED,
                            EnumSet.noneOf(ReimbursementClaimStatus.class),
                    ReimbursementClaimStatus.FINANCE_REJECTED,
                            EnumSet.noneOf(ReimbursementClaimStatus.class),
                    ReimbursementClaimStatus.CANCELLED,
                            EnumSet.noneOf(ReimbursementClaimStatus.class));

    public void assertTransition(ReimbursementClaimStatus from, ReimbursementClaimStatus to) {
        Set<ReimbursementClaimStatus> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Illegal reimbursement claim transition: " + from + " -> " + to);
        }
    }

    public boolean isTerminal(ReimbursementClaimStatus status) {
        return ALLOWED.getOrDefault(status, Set.of()).isEmpty();
    }
}
