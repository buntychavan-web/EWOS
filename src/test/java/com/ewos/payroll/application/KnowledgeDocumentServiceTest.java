package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateKnowledgeDocumentRequest;
import com.ewos.payroll.api.dto.KnowledgeDocumentResponse;
import com.ewos.payroll.domain.KnowledgeDocument;
import com.ewos.payroll.domain.KnowledgeDocumentStatus;
import com.ewos.payroll.domain.KnowledgeSourceType;
import com.ewos.payroll.infrastructure.persistence.KnowledgeDocumentRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceTest {

    @Mock KnowledgeDocumentRepository repository;
    @Mock ClientAccessGuard guard;
    private final PayrollMapper mapper = new PayrollMapper();

    private KnowledgeDocumentService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocumentService(repository, guard, mapper);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(KnowledgeDocument.class)))
                .thenAnswer(
                        inv -> {
                            KnowledgeDocument d = inv.getArgument(0);
                            if (d.getId() == null) {
                                d.setId(UUID.randomUUID());
                            }
                            return d;
                        });
    }

    private static CreateKnowledgeDocumentRequest request() {
        return new CreateKnowledgeDocumentRequest(
                null,
                null,
                KnowledgeSourceType.CBDT_CIRCULAR,
                "Circular on TDS on salaries FY2026-27",
                "Circular No. 12/2026",
                "Clarifies TDS computation for salaried employees",
                "tds,circular,fy2026-27",
                "s3://knowledge/circular-12-2026.pdf",
                LocalDate.of(2026, 4, 1),
                null);
    }

    @Test
    void createStartsANewFamilyAtVersionOneInDraftStatus() {
        KnowledgeDocumentResponse response = service.create(request());

        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(KnowledgeDocumentStatus.DRAFT);
        assertThat(response.documentFamilyId()).isNotNull();
    }

    @Test
    void createNewVersionIncrementsVersionNumberWithinTheSameFamily() {
        UUID familyId = UUID.randomUUID();
        KnowledgeDocument v1 = new KnowledgeDocument();
        v1.setId(UUID.randomUUID());
        v1.setDocumentFamilyId(familyId);
        v1.setVersionNumber(1);
        when(repository.findAllByTenantIdAndDocumentFamilyIdOrderByVersionNumberDesc(
                        tenantId, familyId))
                .thenReturn(List.of(v1));

        KnowledgeDocumentResponse v2 = service.createNewVersion(tenantId, familyId, request());

        assertThat(v2.versionNumber()).isEqualTo(2);
        assertThat(v2.documentFamilyId()).isEqualTo(familyId);
    }

    @Test
    void publishingANewVersionSupersedesThePreviouslyPublishedOne() {
        UUID familyId = UUID.randomUUID();
        UUID newVersionId = UUID.randomUUID();

        KnowledgeDocument previouslyPublished = new KnowledgeDocument();
        previouslyPublished.setDocumentFamilyId(familyId);
        previouslyPublished.setStatus(KnowledgeDocumentStatus.PUBLISHED);

        KnowledgeDocument newVersion = new KnowledgeDocument();
        newVersion.setId(newVersionId);
        newVersion.setDocumentFamilyId(familyId);
        newVersion.setStatus(KnowledgeDocumentStatus.DRAFT);

        when(repository.findByIdAndTenantId(newVersionId, tenantId))
                .thenReturn(Optional.of(newVersion));
        when(repository.findByTenantIdAndDocumentFamilyIdAndStatus(
                        tenantId, familyId, KnowledgeDocumentStatus.PUBLISHED))
                .thenReturn(Optional.of(previouslyPublished));

        KnowledgeDocumentResponse response = service.publish(tenantId, newVersionId);

        assertThat(response.status()).isEqualTo(KnowledgeDocumentStatus.PUBLISHED);
        assertThat(previouslyPublished.getStatus()).isEqualTo(KnowledgeDocumentStatus.SUPERSEDED);
    }
}
