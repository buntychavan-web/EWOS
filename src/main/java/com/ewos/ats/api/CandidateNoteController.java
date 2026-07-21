package com.ewos.ats.api;

import com.ewos.ats.api.dto.AddCandidateNoteRequest;
import com.ewos.ats.api.dto.CandidateNoteResponse;
import com.ewos.ats.application.CandidateNoteService;
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
@RequestMapping("/api/v1/ats/candidates/{candidateId}/notes")
@Tag(name = "ATS Notes", description = "Candidate notes")
public class CandidateNoteController {

    private final CandidateNoteService notes;

    public CandidateNoteController(CandidateNoteService notes) {
        this.notes = notes;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Add a note to a candidate")
    public CandidateNoteResponse add(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody AddCandidateNoteRequest req) {
        return notes.addNote(tenantId, candidateId, req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List a candidate's notes")
    public List<CandidateNoteResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID candidateId) {
        return notes.listForCandidate(tenantId, candidateId);
    }
}
