package com.ewos.ats.api;

import com.ewos.ats.api.dto.CandidateCommunicationResponse;
import com.ewos.ats.api.dto.LogCommunicationRequest;
import com.ewos.ats.application.CandidateCommunicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ats/candidates/{candidateId}/communications")
@Tag(name = "ATS Communications", description = "Candidate communication history")
public class CandidateCommunicationController {

    private final CandidateCommunicationService communications;

    public CandidateCommunicationController(CandidateCommunicationService communications) {
        this.communications = communications;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATS_COMMUNICATE')")
    @Operation(summary = "Log a communication with the candidate")
    public CandidateCommunicationResponse log(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody LogCommunicationRequest req) {
        return communications.log(tenantId, candidateId, req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List a candidate's communication history")
    public List<CandidateCommunicationResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID candidateId) {
        return communications.listForCandidate(tenantId, candidateId);
    }
}
