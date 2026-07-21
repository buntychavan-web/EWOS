package com.ewos.interview.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.ats.domain.JobApplication;
import com.ewos.interview.api.dto.InterviewRoundResponse;
import com.ewos.interview.api.dto.InterviewTemplateResponse;
import com.ewos.interview.api.dto.RoundScorecardSummaryResponse;
import com.ewos.interview.domain.InterviewMode;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.InterviewScorecard;
import com.ewos.interview.domain.InterviewStatus;
import com.ewos.interview.domain.InterviewTemplate;
import com.ewos.interview.domain.InterviewType;
import com.ewos.interview.domain.ScorecardRecommendation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewMapperTest {

    private final InterviewMapper mapper = new InterviewMapper();

    @Test
    void mapsTemplateFields() {
        InterviewTemplate t = new InterviewTemplate();
        t.setTenantId(UUID.randomUUID());
        t.setCompanyId(UUID.randomUUID());
        t.setCode("TECH-01");
        t.setName("Technical screen");
        t.setInterviewType(InterviewType.TECHNICAL);
        t.setDefaultDurationMinutes(45);
        t.setActive(true);

        InterviewTemplateResponse resp = mapper.toResponse(t);
        assertThat(resp.code()).isEqualTo("TECH-01");
        assertThat(resp.interviewType()).isEqualTo(InterviewType.TECHNICAL);
        assertThat(resp.defaultDurationMinutes()).isEqualTo(45);
    }

    @Test
    void mapsRoundFields() {
        JobApplication app = new JobApplication();
        app.setId(UUID.randomUUID());

        InterviewRound r = new InterviewRound();
        r.setTenantId(UUID.randomUUID());
        r.setCompanyId(UUID.randomUUID());
        r.setApplication(app);
        r.setRoundNumber(2);
        r.setName("Deep-dive");
        r.setInterviewType(InterviewType.TECHNICAL);
        r.setDurationMinutes(90);
        r.setMode(InterviewMode.VIDEO);
        r.setStatus(InterviewStatus.SCHEDULED);

        InterviewRoundResponse resp = mapper.toResponse(r);
        assertThat(resp.applicationId()).isEqualTo(app.getId());
        assertThat(resp.roundNumber()).isEqualTo(2);
        assertThat(resp.durationMinutes()).isEqualTo(90);
        assertThat(resp.mode()).isEqualTo(InterviewMode.VIDEO);
        assertThat(resp.status()).isEqualTo(InterviewStatus.SCHEDULED);
    }

    @Test
    void summaryComputesRatingAndRecommendation() {
        InterviewScorecard s1 = new InterviewScorecard();
        s1.setOverallRating(new BigDecimal("8.00"));
        s1.setRecommendation(ScorecardRecommendation.HIRE);

        InterviewScorecard s2 = new InterviewScorecard();
        s2.setOverallRating(new BigDecimal("7.00"));
        s2.setRecommendation(ScorecardRecommendation.STRONG_HIRE);

        RoundScorecardSummaryResponse summary = mapper.summarize(List.of(s1, s2));
        assertThat(summary.submittedCount()).isEqualTo(2);
        assertThat(summary.averageRating()).isEqualByComparingTo("7.50");
        assertThat(summary.leansHire()).isTrue();
        assertThat(summary.weightedRecommendationScore()).isEqualByComparingTo("1.5000");
    }
}
