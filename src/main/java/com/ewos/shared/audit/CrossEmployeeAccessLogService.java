package com.ewos.shared.audit;

import com.ewos.shared.web.CorrelationIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Sprint 27A — writes to the cross-employee access log (PRD §14, audit finding 3.3/3.6). This is
 * the helper every future MSS read (27C's "My Team" drill-down, and the MSS parity extensions
 * across Attendance/Exit/Onboarding/Goals/Competency/Development Plan) must call before or after
 * returning another employee's data — not just for the reads that succeed, but for denied attempts
 * too, per the PRD's enumeration-protection requirement (§5.3/§17): the real reason an MSS lookup
 * was denied is captured here, even though the HTTP response never distinguishes it.
 *
 * <p>Correlation id and client IP are resolved the same way the rest of the platform already does —
 * {@link CorrelationIdFilter#MDC_KEY} via SLF4J MDC, and {@code X-Forwarded-For} / {@code
 * getRemoteAddr()} via the current request — so call sites only need to supply the business facts
 * (who, on whom, what, granted or not, why).
 */
@Service
public class CrossEmployeeAccessLogService {

    private final CrossEmployeeAccessLogRepository logs;

    public CrossEmployeeAccessLogService(CrossEmployeeAccessLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void logGranted(
            UUID tenantId, UUID actorEmployeeId, UUID targetEmployeeId, String action) {
        log(tenantId, actorEmployeeId, targetEmployeeId, action, true, null);
    }

    @Transactional
    public void logDenied(
            UUID tenantId,
            UUID actorEmployeeId,
            UUID targetEmployeeId,
            String action,
            String reason) {
        log(tenantId, actorEmployeeId, targetEmployeeId, action, false, reason);
    }

    @Transactional
    public void log(
            UUID tenantId,
            UUID actorEmployeeId,
            UUID targetEmployeeId,
            String action,
            boolean granted,
            String reason) {
        CrossEmployeeAccessLog entry = new CrossEmployeeAccessLog();
        entry.setTenantId(tenantId);
        entry.setActorEmployeeId(actorEmployeeId);
        entry.setTargetEmployeeId(targetEmployeeId);
        entry.setAction(action);
        entry.setGranted(granted);
        entry.setReason(reason);
        entry.setOccurredAt(Instant.now());
        entry.setIpAddress(currentClientIp());
        entry.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        logs.save(entry);
    }

    @Transactional(readOnly = true)
    public List<CrossEmployeeAccessLog> historyForTarget(UUID tenantId, UUID targetEmployeeId) {
        return logs.findAllByTenantIdAndTargetEmployeeIdOrderByOccurredAtDesc(
                tenantId, targetEmployeeId);
    }

    @Transactional(readOnly = true)
    public List<CrossEmployeeAccessLog> historyForActor(UUID tenantId, UUID actorEmployeeId) {
        return logs.findAllByTenantIdAndActorEmployeeIdOrderByOccurredAtDesc(
                tenantId, actorEmployeeId);
    }

    private static String currentClientIp() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        var request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
