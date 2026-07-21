package com.ewos.offer.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.JobApplication;
import com.ewos.offer.api.dto.OfferResponse;
import com.ewos.offer.api.dto.OfferTemplateResponse;
import com.ewos.offer.domain.EmploymentType;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferStatus;
import com.ewos.offer.domain.OfferTemplate;
import com.ewos.recruitment.domain.JobRequisition;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfferMapperTest {

    private final OfferMapper mapper = new OfferMapper();

    @Test
    void mapsTemplate() {
        OfferTemplate t = new OfferTemplate();
        t.setTenantId(UUID.randomUUID());
        t.setCompanyId(UUID.randomUUID());
        t.setCode("STD");
        t.setName("Standard offer");
        t.setBodyTemplate("Dear ${candidate}");
        t.setDefaultCurrency("INR");
        t.setDefaultExpiryDays(14);
        t.setActive(true);
        OfferTemplateResponse resp = mapper.toResponse(t);
        assertThat(resp.code()).isEqualTo("STD");
        assertThat(resp.defaultExpiryDays()).isEqualTo(14);
        assertThat(resp.defaultCurrency()).isEqualTo("INR");
    }

    @Test
    void mapsOfferFields() {
        JobApplication app = new JobApplication();
        app.setId(UUID.randomUUID());
        Candidate cand = new Candidate();
        cand.setId(UUID.randomUUID());
        JobRequisition req = new JobRequisition();
        req.setId(UUID.randomUUID());

        Offer o = new Offer();
        o.setTenantId(UUID.randomUUID());
        o.setCompanyId(UUID.randomUUID());
        o.setOfferNumber("OFF-1");
        o.setApplication(app);
        o.setCandidate(cand);
        o.setJobRequisition(req);
        o.setDesignation("Senior Engineer");
        o.setEmploymentType(EmploymentType.FULL_TIME);
        o.setCurrency("INR");
        o.setBaseSalary(new BigDecimal("1000"));
        o.setTotalCtc(new BigDecimal("1000"));
        o.setStatus(OfferStatus.APPROVED);
        o.setVersion(1);

        OfferResponse resp = mapper.toResponse(o);
        assertThat(resp.offerNumber()).isEqualTo("OFF-1");
        assertThat(resp.applicationId()).isEqualTo(app.getId());
        assertThat(resp.candidateId()).isEqualTo(cand.getId());
        assertThat(resp.jobRequisitionId()).isEqualTo(req.getId());
        assertThat(resp.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(resp.status()).isEqualTo(OfferStatus.APPROVED);
    }
}
