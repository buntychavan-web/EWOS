package com.ewos.interview.api;

import com.ewos.interview.api.dto.AddInterviewParticipantRequest;
import com.ewos.interview.api.dto.InterviewParticipantResponse;
import com.ewos.interview.api.dto.UpdateAttendanceRequest;
import com.ewos.interview.application.InterviewPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interviews")
@Tag(name = "Interview Panel", description = "Panel composition for interview rounds")
public class InterviewPanelController {

    private final InterviewPanelService panel;

    public InterviewPanelController(InterviewPanelService panel) {
        this.panel = panel;
    }

    @PostMapping("/rounds/{roundId}/participants")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Add a panel member to the round")
    public InterviewParticipantResponse add(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID roundId,
            @Valid @RequestBody AddInterviewParticipantRequest req) {
        return panel.addParticipant(tenantId, roundId, req);
    }

    @PutMapping("/participants/{participantId}/attendance")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Update a panel member's attendance")
    public InterviewParticipantResponse updateAttendance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID participantId,
            @Valid @RequestBody UpdateAttendanceRequest req) {
        return panel.updateAttendance(tenantId, participantId, req);
    }

    @DeleteMapping("/participants/{participantId}")
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Remove a panel member")
    public ResponseEntity<Void> remove(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID participantId) {
        panel.removeParticipant(tenantId, participantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rounds/{roundId}/participants")
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "List panel members for a round")
    public List<InterviewParticipantResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID roundId) {
        return panel.listForRound(tenantId, roundId);
    }
}
