package com.ewos.interview.api;

import com.ewos.interview.api.dto.InterviewScorecardResponse;
import com.ewos.interview.api.dto.RoundScorecardSummaryResponse;
import com.ewos.interview.api.dto.SubmitScorecardRequest;
import com.ewos.interview.application.InterviewScorecardService;
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
@RequestMapping("/api/v1/interviews/rounds/{roundId}/scorecards")
@Tag(name = "Interview Scorecards", description = "Post-interview evaluations")
public class InterviewScorecardController {

    private final InterviewScorecardService scorecards;

    public InterviewScorecardController(InterviewScorecardService scorecards) {
        this.scorecards = scorecards;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INTERVIEW_SUBMIT_SCORECARD')")
    @Operation(summary = "Submit or update a scorecard for this round")
    public InterviewScorecardResponse submit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID roundId,
            @Valid @RequestBody SubmitScorecardRequest req) {
        return scorecards.submit(tenantId, roundId, req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "List scorecards for a round")
    public List<InterviewScorecardResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID roundId) {
        return scorecards.listForRound(tenantId, roundId);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('INTERVIEW_READ')")
    @Operation(summary = "Aggregate scorecard summary for a round")
    public RoundScorecardSummaryResponse summary(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID roundId) {
        return scorecards.summarize(tenantId, roundId);
    }
}
