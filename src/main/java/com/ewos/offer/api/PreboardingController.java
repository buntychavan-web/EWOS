package com.ewos.offer.api;

import com.ewos.offer.api.dto.ConfirmJoiningRequest;
import com.ewos.offer.api.dto.PreboardingChecklistResponse;
import com.ewos.offer.api.dto.PreboardingTaskInstanceResponse;
import com.ewos.offer.api.dto.UpdateTaskStatusRequest;
import com.ewos.offer.application.PreboardingService;
import com.ewos.offer.domain.preboarding.PreboardingChecklistStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preboarding")
@Tag(name = "Pre-Boarding", description = "Pre-joining checklists and tasks")
public class PreboardingController {

    private final PreboardingService preboarding;

    public PreboardingController(PreboardingService preboarding) {
        this.preboarding = preboarding;
    }

    @PostMapping("/checklists/from-offer/{offerId}")
    @PreAuthorize("hasAuthority('PREBOARDING_WRITE')")
    @Operation(summary = "Create the checklist for an ACCEPTED offer (idempotent)")
    public PreboardingChecklistResponse createFromOffer(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID offerId) {
        return preboarding.createChecklistForOffer(tenantId, offerId);
    }

    @GetMapping("/checklists/{id}")
    @PreAuthorize("hasAuthority('PREBOARDING_READ')")
    @Operation(summary = "Fetch a checklist")
    public PreboardingChecklistResponse getChecklist(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return preboarding.getById(tenantId, id);
    }

    @GetMapping("/checklists/{id}/tasks")
    @PreAuthorize("hasAuthority('PREBOARDING_READ')")
    @Operation(summary = "List tasks on a checklist")
    public List<PreboardingTaskInstanceResponse> listTasks(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return preboarding.tasksForChecklist(tenantId, id);
    }

    @PostMapping("/tasks/{id}/remind")
    @PreAuthorize("hasAuthority('PREBOARDING_WRITE')")
    @Operation(summary = "Send a reminder for a pending / in-progress pre-boarding task")
    public PreboardingTaskInstanceResponse remindTask(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return preboarding.sendTaskReminder(tenantId, id);
    }

    @PutMapping("/tasks/{id}/status")
    @PreAuthorize("hasAuthority('PREBOARDING_WRITE')")
    @Operation(summary = "Update a task's status")
    public PreboardingTaskInstanceResponse updateTaskStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskStatusRequest req) {
        return preboarding.updateTaskStatus(tenantId, id, req);
    }

    @PostMapping("/checklists/{id}/confirm-joining")
    @PreAuthorize("hasAuthority('PREBOARDING_WRITE')")
    @Operation(summary = "Confirm the candidate joined and hand off to Employee master")
    public PreboardingChecklistResponse confirmJoining(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmJoiningRequest req) {
        return preboarding.confirmJoining(tenantId, id, req);
    }

    @PostMapping("/checklists/{id}/no-show")
    @PreAuthorize("hasAuthority('PREBOARDING_WRITE')")
    @Operation(summary = "Mark the checklist NO_SHOW with a reason")
    public PreboardingChecklistResponse markNoShow(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) String reason) {
        return preboarding.markNoShow(tenantId, id, reason);
    }

    @PostMapping("/checklists/{id}/cancel")
    @PreAuthorize("hasAuthority('PREBOARDING_WRITE')")
    @Operation(summary = "Cancel the checklist with a reason")
    public PreboardingChecklistResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) String reason) {
        return preboarding.cancel(tenantId, id, reason);
    }

    @GetMapping("/checklists")
    @PreAuthorize("hasAuthority('PREBOARDING_READ')")
    @Operation(summary = "List checklists for a company by status")
    public List<PreboardingChecklistResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam PreboardingChecklistStatus status) {
        return preboarding.byStatus(tenantId, companyId, status);
    }
}
