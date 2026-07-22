package com.ewos.learning.api;

import com.ewos.learning.api.dto.CreateSessionRequest;
import com.ewos.learning.api.dto.SessionResponse;
import com.ewos.learning.application.TrainingSessionService;
import com.ewos.learning.domain.TrainingSessionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/training-sessions")
@Tag(name = "Training Sessions", description = "Scheduled sessions + training calendar")
public class TrainingSessionController {

    private final TrainingSessionService sessions;

    public TrainingSessionController(TrainingSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEARNING_WRITE')")
    @Operation(summary = "Schedule a training session")
    public ResponseEntity<SessionResponse> schedule(@Valid @RequestBody CreateSessionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessions.schedule(req));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('LEARNING_WRITE')")
    @Operation(summary = "Mark a session as in progress")
    public SessionResponse start(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return sessions.start(tenantId, id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('LEARNING_WRITE')")
    @Operation(summary = "Complete a session")
    public SessionResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return sessions.complete(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('LEARNING_WRITE')")
    @Operation(summary = "Cancel a session")
    public SessionResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) String notes) {
        return sessions.cancel(tenantId, id, notes);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "Fetch a training session by id")
    public SessionResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return sessions.getById(tenantId, id);
    }

    @GetMapping("/by-course")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List sessions for a course, newest first")
    public List<SessionResponse> byCourse(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID courseId) {
        return sessions.forCourse(tenantId, courseId);
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List sessions by status")
    public List<SessionResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam TrainingSessionStatus status) {
        return sessions.byStatus(tenantId, companyId, status);
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List scheduled sessions in a time window (training calendar)")
    public List<SessionResponse> calendar(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return sessions.calendar(tenantId, companyId, from, to);
    }
}
