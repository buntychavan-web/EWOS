package com.ewos.exit.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.exit.api.dto.AlumniResponse;
import com.ewos.exit.api.dto.ClearanceResponse;
import com.ewos.exit.api.dto.DocumentResponse;
import com.ewos.exit.api.dto.InterviewResponse;
import com.ewos.exit.api.dto.KtItemResponse;
import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.domain.AlumniRecord;
import com.ewos.exit.domain.ClearanceDepartment;
import com.ewos.exit.domain.ClearanceStatus;
import com.ewos.exit.domain.ExitClearance;
import com.ewos.exit.domain.ExitDocument;
import com.ewos.exit.domain.ExitDocumentType;
import com.ewos.exit.domain.ExitInterview;
import com.ewos.exit.domain.KnowledgeTransferItem;
import com.ewos.exit.domain.RehireEligibility;
import com.ewos.exit.domain.Resignation;
import com.ewos.exit.domain.ResignationStatus;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExitMapperTest {

    private final ExitMapper mapper = new ExitMapper();

    @Test
    void resignationRoundTrip() {
        Resignation r = new Resignation();
        setId(r, UUID.randomUUID());
        r.setTenantId(UUID.randomUUID());
        r.setCompanyId(UUID.randomUUID());
        r.setNoticePeriodDays(60);
        r.setStatus(ResignationStatus.SUBMITTED);
        r.setSubmittedAt(Instant.EPOCH);
        r.setRehireEligibility(RehireEligibility.YES);
        ResignationResponse res = mapper.toResponse(r);
        assertThat(res.noticePeriodDays()).isEqualTo(60);
        assertThat(res.status()).isEqualTo(ResignationStatus.SUBMITTED);
        assertThat(res.rehireEligibility()).isEqualTo(RehireEligibility.YES);
    }

    @Test
    void clearanceRoundTrip() {
        ExitClearance c = new ExitClearance();
        setId(c, UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setDepartment(ClearanceDepartment.IT);
        c.setStatus(ClearanceStatus.PENDING);
        c.setNotes("Return laptop");
        ClearanceResponse res = mapper.toResponse(c);
        assertThat(res.department()).isEqualTo(ClearanceDepartment.IT);
        assertThat(res.status()).isEqualTo(ClearanceStatus.PENDING);
    }

    @Test
    void ktItemRoundTrip() {
        KnowledgeTransferItem k = new KnowledgeTransferItem();
        setId(k, UUID.randomUUID());
        k.setTenantId(UUID.randomUUID());
        k.setTopic("Payments module");
        k.setCompleted(false);
        KtItemResponse res = mapper.toResponse(k);
        assertThat(res.topic()).isEqualTo("Payments module");
        assertThat(res.completed()).isFalse();
    }

    @Test
    void interviewRoundTrip() {
        ExitInterview i = new ExitInterview();
        setId(i, UUID.randomUUID());
        i.setTenantId(UUID.randomUUID());
        i.setInterviewerName("Jane HR");
        i.setRating(new BigDecimal("4.20"));
        i.setWouldRecommend(true);
        InterviewResponse res = mapper.toResponse(i);
        assertThat(res.interviewerName()).isEqualTo("Jane HR");
        assertThat(res.rating()).isEqualTo(new BigDecimal("4.20"));
        assertThat(res.wouldRecommend()).isTrue();
    }

    @Test
    void documentRoundTrip() {
        ExitDocument d = new ExitDocument();
        setId(d, UUID.randomUUID());
        d.setTenantId(UUID.randomUUID());
        d.setDocumentType(ExitDocumentType.RELIEVING_LETTER);
        d.setDocumentUri("s3://exit/relieving.pdf");
        d.setReferenceNumber("REL-0001");
        DocumentResponse res = mapper.toResponse(d);
        assertThat(res.documentType()).isEqualTo(ExitDocumentType.RELIEVING_LETTER);
        assertThat(res.referenceNumber()).isEqualTo("REL-0001");
    }

    @Test
    void alumniRoundTrip() {
        AlumniRecord a = new AlumniRecord();
        setId(a, UUID.randomUUID());
        a.setTenantId(UUID.randomUUID());
        a.setCompanyId(UUID.randomUUID());
        a.setExitedOn(LocalDate.of(2026, 1, 15));
        a.setAlumniEmail("ex.emp@example.com");
        a.setStayInTouch(true);
        a.setRehireEligibility(RehireEligibility.WITH_APPROVAL);
        AlumniResponse res = mapper.toResponse(a);
        assertThat(res.exitedOn()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(res.stayInTouch()).isTrue();
        assertThat(res.rehireEligibility()).isEqualTo(RehireEligibility.WITH_APPROVAL);
    }

    private static void setId(Object entity, UUID id) {
        try {
            Class<?> c = entity.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, id);
                    return;
                } catch (NoSuchFieldException ignore) {
                    c = c.getSuperclass();
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
