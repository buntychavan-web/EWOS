package com.ewos.interview.api;

import com.ewos.interview.api.dto.CancelInterviewRoundRequest;
import com.ewos.interview.api.dto.CreateInterviewRoundRequest;
import com.ewos.interview.api.dto.InterviewRoundResponse;
import com.ewos.interview.api.dto.RecordInterviewDecisionRequest;
import com.ewos.interview.api.dto.ScheduleInterviewRoundRequest;
import com.ewos.interview.application.InterviewRoundService;
import com.ewos.interview.domain.InterviewStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interviews/rounds")
@Tag(
        name = "Interview Rounds",
        description =
                "Scheduled interview rounds attached to an ATS application. Full lifecycle from"
                        + " DRAFT through decision.")
public class InterviewRoundController {

    private final InterviewRoundService rounds;

    public InterviewRoundController(InterviewRoundService rounds) {
        this.rounds = rounds;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Create a DRAFT round for an application")
    public ResponseEntity<InterviewRoundResponse> create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateInterviewRoundRequest req) {
        InterviewRoundResponse created = rounds.create(tenantId, req);
        return ResponseEntity.created(URI.create("/api/v1/interviews/rounds/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Schedule a DRAFT / CANCELLED round")
    public InterviewRoundResponse schedule(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleInterviewRoundRequest req) {
        return rounds.schedule(tenantId, id, req);
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Reschedule a SCHEDULED / RESCHEDULED round")
    public InterviewRoundResponse reschedule(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleInterviewRoundRequest req) {
        return rounds.reschedule(tenantId, id, req);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Mark a round IN_PROGRESS")
    public InterviewRoundResponse start(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return rounds.start(tenantId, id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Mark a round COMPLETED")
    public InterviewRoundResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return rounds.complete(tenantId, id);
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Mark a round NO_SHOW")
    public InterviewRoundResponse markNoShow(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return rounds.markNoShow(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Cancel a round with a reason")
    public InterviewRoundResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CancelInterviewRoundRequest req) {
        return rounds.cancel(tenantId, id, req);
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Record the per-round hiring decision")
    public InterviewRoundResponse decide(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody RecordInterviewDecisionRequest req) {
        return rounds.decide(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "Fetch a round by ID")
    public InterviewRoundResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return rounds.getById(tenantId, id);
    }

    @GetMapping("/by-application/{applicationId}")
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "List rounds for an application in round-number order")
    public List<InterviewRoundResponse> forApplication(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID applicationId) {
        return rounds.forApplication(tenantId, applicationId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "List rounds for a company by status")
    public List<InterviewRoundResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam InterviewStatus status) {
        return rounds.byStatus(tenantId, companyId, status);
    }

    @GetMapping("/scheduled")
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "Rounds with scheduled_start inside a time window")
    public List<InterviewRoundResponse> scheduledBetween(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return rounds.scheduledBetween(tenantId, companyId, from, to);
    }
}
