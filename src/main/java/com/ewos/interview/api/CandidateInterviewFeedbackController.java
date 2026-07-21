package com.ewos.interview.api;

import com.ewos.interview.api.dto.CandidateInterviewFeedbackResponse;
import com.ewos.interview.api.dto.SubmitCandidateFeedbackRequest;
import com.ewos.interview.application.CandidateInterviewFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/interviews/rounds/{roundId}/candidate-feedback")
@Tag(
        name = "Candidate Interview Feedback",
        description = "Candidate's own feedback on the interview experience")
public class CandidateInterviewFeedbackController {

    private final CandidateInterviewFeedbackService feedback;

    public CandidateInterviewFeedbackController(CandidateInterviewFeedbackService feedback) {
        this.feedback = feedback;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INTERVIEW_WRITE')")
    @Operation(summary = "Submit or update candidate feedback for a round")
    public CandidateInterviewFeedbackResponse submit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID roundId,
            @Valid @RequestBody SubmitCandidateFeedbackRequest req) {
        return feedback.submit(tenantId, roundId, req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "Fetch candidate feedback for a round")
    public CandidateInterviewFeedbackResponse get(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID roundId) {
        return feedback.forRound(tenantId, roundId);
    }
}
