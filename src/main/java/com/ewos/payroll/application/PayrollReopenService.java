package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.PayrollRunReopenAuthorizationResponse;
import com.ewos.payroll.api.dto.ReopenPayrollRunRequest;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunReopenAuthorization;
import com.ewos.payroll.domain.PayrollRunReopenAuthorizationStatus;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.PayrollRunReopenAuthorizationRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24L item 2 — Payroll Reopen / Correction Framework, the run side. A FROZEN {@link
 * PayrollRun} is never mutated by this service — its status stays FROZEN, its payslips stay exactly
 * as computed. What this authorizes is one new {@code SUPPLEMENTARY} run through {@code
 * PayrollRunService#startSupplementary}'s post-freeze block (see that method), which is where the
 * actual correction — new, equally immutable payslips acting as reversal entries — happens.
 */
@Service
@Transactional
public class PayrollReopenService {

    private final PayrollRunReopenAuthorizationRepository authorizations;
    private final PayrollRunRepository runs;
    private final ClientAccessGuard guard;
    private final ApplicationEventPublisher events;

    public PayrollReopenService(
            PayrollRunReopenAuthorizationRepository authorizations,
            PayrollRunRepository runs,
            ClientAccessGuard guard,
            ApplicationEventPublisher events) {
        this.authorizations = authorizations;
        this.runs = runs;
        this.guard = guard;
        this.events = events;
    }

    /** Proper authorization is {@code PAYROLL_ADMIN}, enforced at the controller. */
    public PayrollRunReopenAuthorizationResponse authorizeReopen(
            UUID tenantId, UUID runId, ReopenPayrollRunRequest request) {
        PayrollRun run =
                runs.findByIdAndTenantId(runId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Payroll run not found"));
        guard.requireAccessForCompany(run.getCompanyId());
        if (run.getStatus() != PayrollRunStatus.FROZEN) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only FROZEN runs need a reopen authorization; current status is "
                            + run.getStatus()
                            + " (FINALIZED runs already accept supplementary corrections"
                            + " directly)");
        }
        if (authorizations.findActiveForRun(tenantId, runId).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "An active reopen authorization already exists for this run — revoke it"
                            + " before requesting another");
        }
        UUID actor = requireActor();

        PayrollRunReopenAuthorization authorization = new PayrollRunReopenAuthorization();
        authorization.setTenantId(tenantId);
        authorization.setCompanyId(run.getCompanyId());
        authorization.setPayrollRun(run);
        authorization.setReason(request.reason());
        authorization.setAuthorizedBy(actor);
        authorization.setAuthorizedAt(Instant.now());
        authorization.setStatus(PayrollRunReopenAuthorizationStatus.ACTIVE);
        PayrollRunReopenAuthorization saved = authorizations.save(authorization);

        events.publishEvent(
                new PayrollEvent(
                        PayrollEventType.RUN_REOPEN_AUTHORIZED,
                        tenantId,
                        run.getCompanyId(),
                        null,
                        run.getPayrollPeriod() != null ? run.getPayrollPeriod().getId() : null,
                        run.getId(),
                        null,
                        null,
                        null,
                        actor,
                        Instant.now()));
        return toResponse(saved);
    }

    public PayrollRunReopenAuthorizationResponse revoke(UUID tenantId, UUID authorizationId) {
        PayrollRunReopenAuthorization authorization =
                authorizations
                        .findById(authorizationId)
                        .filter(a -> a.getTenantId().equals(tenantId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Reopen authorization not found"));
        guard.requireAccessForCompany(authorization.getCompanyId());
        if (authorization.getStatus() != PayrollRunReopenAuthorizationStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only an ACTIVE authorization can be revoked; current status is "
                            + authorization.getStatus());
        }
        authorization.setStatus(PayrollRunReopenAuthorizationStatus.REVOKED);
        authorization.setRevokedBy(requireActor());
        authorization.setRevokedAt(Instant.now());
        return toResponse(authorization);
    }

    @Transactional(readOnly = true)
    public List<PayrollRunReopenAuthorizationResponse> historyForRun(UUID tenantId, UUID runId) {
        PayrollRun run =
                runs.findByIdAndTenantId(runId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Payroll run not found"));
        guard.requireAccessForCompany(run.getCompanyId());
        return authorizations.findAllForRun(tenantId, runId).stream()
                .map(PayrollReopenService::toResponse)
                .toList();
    }

    private static PayrollRunReopenAuthorizationResponse toResponse(
            PayrollRunReopenAuthorization a) {
        return new PayrollRunReopenAuthorizationResponse(
                a.getId(),
                a.getPayrollRun().getId(),
                a.getReason(),
                a.getAuthorizedBy(),
                a.getAuthorizedAt(),
                a.getStatus(),
                a.getConsumedByRun() != null ? a.getConsumedByRun().getId() : null,
                a.getConsumedAt(),
                a.getRevokedBy(),
                a.getRevokedAt());
    }

    private static UUID requireActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user required for this action");
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action", e);
        }
    }
}
