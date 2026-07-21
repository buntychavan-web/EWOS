package com.ewos.ats.api;

import com.ewos.ats.api.dto.CandidateTimelineEventResponse;
import com.ewos.ats.application.CandidateTimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ats")
@Tag(name = "ATS Timeline", description = "Append-only timeline for candidates and applications")
public class CandidateTimelineController {

    private final CandidateTimelineService timeline;

    public CandidateTimelineController(CandidateTimelineService timeline) {
        this.timeline = timeline;
    }

    @GetMapping("/candidates/{candidateId}/timeline")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "Fetch a candidate's timeline (most recent first)")
    public List<CandidateTimelineEventResponse> forCandidate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID candidateId) {
        return timeline.forCandidate(tenantId, candidateId);
    }

    @GetMapping("/applications/{applicationId}/timeline")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "Fetch a single application's timeline")
    public List<CandidateTimelineEventResponse> forApplication(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID applicationId) {
        return timeline.forApplication(tenantId, applicationId);
    }
}
