package com.ewos.exit.domain;

import com.ewos.shared.exception.ApiException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Guards state transitions for resignations. */
@Component
public class ResignationLifecyclePolicy {

    private static final Map<ResignationStatus, Set<ResignationStatus>> ALLOWED =
            Map.of(
                    ResignationStatus.SUBMITTED,
                            EnumSet.of(
                                    ResignationStatus.ACCEPTED,
                                    ResignationStatus.WITHDRAWN,
                                    ResignationStatus.CANCELLED),
                    ResignationStatus.ACCEPTED,
                            EnumSet.of(
                                    ResignationStatus.IN_NOTICE,
                                    ResignationStatus.EXITED,
                                    ResignationStatus.CANCELLED),
                    ResignationStatus.IN_NOTICE,
                            EnumSet.of(ResignationStatus.EXITED, ResignationStatus.CANCELLED),
                    ResignationStatus.EXITED, EnumSet.noneOf(ResignationStatus.class),
                    ResignationStatus.WITHDRAWN, EnumSet.noneOf(ResignationStatus.class),
                    ResignationStatus.CANCELLED, EnumSet.noneOf(ResignationStatus.class));

    public void assertTransition(ResignationStatus from, ResignationStatus to) {
        Set<ResignationStatus> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Illegal resignation transition: " + from + " -> " + to);
        }
    }

    public boolean isTerminal(ResignationStatus status) {
        return status == ResignationStatus.EXITED
                || status == ResignationStatus.WITHDRAWN
                || status == ResignationStatus.CANCELLED;
    }

    public boolean isOpen(ResignationStatus status) {
        return !isTerminal(status);
    }
}
