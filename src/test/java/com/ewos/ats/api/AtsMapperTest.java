package com.ewos.ats.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.ats.api.dto.CandidateResponse;
import com.ewos.ats.api.dto.JobApplicationResponse;
import com.ewos.ats.domain.ApplicationStatus;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateSource;
import com.ewos.ats.domain.CandidateStatus;
import com.ewos.ats.domain.JobApplication;
import com.ewos.recruitment.domain.JobPosition;
import com.ewos.recruitment.domain.JobRequisition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AtsMapperTest {

    private final AtsMapper mapper = new AtsMapper();

    @Test
    void mapsCandidateFields() {
        Candidate c = new Candidate();
        c.setTenantId(UUID.randomUUID());
        c.setCompanyId(UUID.randomUUID());
        c.setCandidateNumber("CAND-202607-000001");
        c.setFirstName("Ada");
        c.setLastName("Lovelace");
        c.setEmail("ada@example.com");
        c.setSource(CandidateSource.LINKEDIN);
        c.setStatus(CandidateStatus.ACTIVE);

        CandidateResponse resp = mapper.toResponse(c);
        assertThat(resp.firstName()).isEqualTo("Ada");
        assertThat(resp.lastName()).isEqualTo("Lovelace");
        assertThat(resp.candidateNumber()).isEqualTo("CAND-202607-000001");
        assertThat(resp.source()).isEqualTo(CandidateSource.LINKEDIN);
        assertThat(resp.status()).isEqualTo(CandidateStatus.ACTIVE);
    }

    @Test
    void mapsApplicationFields() {
        JobPosition pos = new JobPosition();
        pos.setId(UUID.randomUUID());
        JobRequisition req = new JobRequisition();
        req.setId(UUID.randomUUID());
        req.setJobPosition(pos);
        Candidate cand = new Candidate();
        cand.setId(UUID.randomUUID());

        JobApplication a = new JobApplication();
        a.setTenantId(UUID.randomUUID());
        a.setCompanyId(UUID.randomUUID());
        a.setApplicationNumber("APP-2026-42");
        a.setCandidate(cand);
        a.setJobRequisition(req);
        a.setSource(CandidateSource.REFERRAL);
        a.setStatus(ApplicationStatus.SHORTLISTED);

        JobApplicationResponse resp = mapper.toResponse(a);
        assertThat(resp.applicationNumber()).isEqualTo("APP-2026-42");
        assertThat(resp.candidateId()).isEqualTo(cand.getId());
        assertThat(resp.jobRequisitionId()).isEqualTo(req.getId());
        assertThat(resp.status()).isEqualTo(ApplicationStatus.SHORTLISTED);
    }
}
