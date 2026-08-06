package com.ewos.exit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.exit.domain.ExitDocumentTemplate;
import com.ewos.exit.domain.ExitDocumentType;
import com.ewos.exit.domain.Resignation;
import com.ewos.exit.domain.ResignationType;
import com.ewos.exit.infrastructure.persistence.ResignationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ExitDocumentGenerationServiceTest {

    @Mock ResignationRepository resignations;
    @Mock ClientAccessGuard guard;
    @Mock ExitDocumentTemplateService templates;
    @Mock ExitDocumentPdfGenerationService pdf;

    private ExitDocumentGenerationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ExitDocumentGenerationService(resignations, guard, templates, pdf);
    }

    private Resignation resignation() {
        Employee employee = new Employee();
        employee.setDisplayName("Jordan Rivera");
        employee.setEmployeeNumber("EMP-42");
        employee.setHireDate(LocalDate.of(2020, 1, 15));

        Resignation r = new Resignation();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setCompanyId(companyId);
        r.setEmployee(employee);
        r.setResignationType(ResignationType.SELF_RESIGNATION);
        r.setIntendedLastDay(LocalDate.of(2026, 4, 30));
        r.setNoticePeriodDays(30);
        return r;
    }

    @Test
    void generateThrowsWhenNoTemplateIsConfigured() {
        Resignation r = resignation();
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(templates.resolveEffective(
                        tenantId, companyId, null, ExitDocumentType.RELIEVING_LETTER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        tenantId, r.getId(), ExitDocumentType.RELIEVING_LETTER))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generateSubstitutesTokensAndRendersThePdf() {
        Resignation r = resignation();
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        ExitDocumentTemplate template = new ExitDocumentTemplate();
        template.setTitle("Relieving Letter");
        template.setBodyTemplate(
                "Dear {{employeeName}} ({{employeeNumber}}), your last working day is"
                        + " {{lastWorkingDate}} after {{noticePeriodDays}} days notice.");
        when(templates.resolveEffective(
                        tenantId, companyId, null, ExitDocumentType.RELIEVING_LETTER))
                .thenReturn(Optional.of(template));
        when(pdf.generate(any(), any(), any())).thenReturn(new byte[] {1, 2, 3});

        byte[] result = service.generate(tenantId, r.getId(), ExitDocumentType.RELIEVING_LETTER);

        assertThat(result).isEqualTo(new byte[] {1, 2, 3});
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(pdf).generate(eq("Relieving Letter"), bodyCaptor.capture(), eq(LocalDate.now()));
        assertThat(bodyCaptor.getValue())
                .isEqualTo(
                        "Dear Jordan Rivera (EMP-42), your last working day is 2026-04-30 after 30"
                                + " days notice.");
    }

    @Test
    void generateChecksAccessForTheResignationsCompany() {
        Resignation r = resignation();
        when(resignations.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(templates.resolveEffective(
                        tenantId, companyId, null, ExitDocumentType.RELIEVING_LETTER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.generate(tenantId, r.getId(), ExitDocumentType.RELIEVING_LETTER));

        verify(guard).requireAccessForCompany(companyId);
    }
}
