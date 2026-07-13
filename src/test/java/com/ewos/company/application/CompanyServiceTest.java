package com.ewos.company.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.common.exception.ApiException;
import com.ewos.company.api.dto.CompanyProfile;
import com.ewos.company.api.dto.CompanyResponse;
import com.ewos.company.api.dto.CreateCompanyRequest;
import com.ewos.company.api.dto.UpdateCompanyProfileRequest;
import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanyVersion;
import com.ewos.company.domain.Tenant;
import com.ewos.company.infrastructure.persistence.CompanyPolicyAssignmentRepository;
import com.ewos.company.infrastructure.persistence.CompanyRepository;
import com.ewos.company.infrastructure.persistence.CompanyVersionRepository;
import com.ewos.company.infrastructure.persistence.TenantRepository;
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
class CompanyServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock CompanyVersionRepository versionRepository;
    @Mock CompanyPolicyAssignmentRepository policyAssignmentRepository;
    @Mock TenantRepository tenantRepository;

    private CompanyService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        service =
                new CompanyService(
                        companyRepository,
                        versionRepository,
                        policyAssignmentRepository,
                        tenantRepository);
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setCode("DEFAULT");
        tenant.setName("Default Tenant");

        lenient().when(tenantRepository.findByCode("DEFAULT")).thenReturn(Optional.of(tenant));
        lenient().when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        lenient()
                .when(companyRepository.save(any(Company.class)))
                .thenAnswer(
                        inv -> {
                            Company c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
        lenient()
                .when(versionRepository.save(any(CompanyVersion.class)))
                .thenAnswer(
                        inv -> {
                            CompanyVersion v = inv.getArgument(0);
                            if (v.getId() == null) {
                                v.setId(UUID.randomUUID());
                            }
                            return v;
                        });
    }

    @Test
    void createBlankCompanyIssuesInitialOpenVersion() {
        CreateCompanyRequest req =
                new CreateCompanyRequest(
                        null, "ACME", profile(LocalDate.of(2026, 1, 1)), null, null);

        CompanyResponse resp = service.create(req);

        assertThat(resp.code()).isEqualTo("ACME");
        assertThat(resp.tenantId()).isEqualTo(tenant.getId());
        assertThat(resp.active()).isTrue();
        assertThat(resp.currentVersion()).isNotNull();
        assertThat(resp.currentVersion().effectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(resp.currentVersion().effectiveTo()).isNull();
        verify(versionRepository).save(any(CompanyVersion.class));
    }

    @Test
    void createRejectsDuplicateCodePerTenant() {
        when(companyRepository.existsByTenantAndCode(tenant, "ACME")).thenReturn(true);
        CreateCompanyRequest req =
                new CreateCompanyRequest(
                        null, "ACME", profile(LocalDate.of(2026, 1, 1)), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateProfileClosesPreviousAndOpensNew() {
        Company company = existingCompany();
        CompanyVersion current = new CompanyVersion();
        current.setCompany(company);
        current.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        current.setEffectiveTo(null);

        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(versionRepository.findByCompanyAndEffectiveToIsNull(company))
                .thenReturn(Optional.of(current));

        LocalDate newFrom = LocalDate.of(2026, 4, 1);
        service.updateProfile(company.getId(), new UpdateCompanyProfileRequest(profile(newFrom)));

        assertThat(current.getEffectiveTo()).isEqualTo(newFrom.minusDays(1));

        ArgumentCaptor<CompanyVersion> captor = ArgumentCaptor.forClass(CompanyVersion.class);
        verify(versionRepository).save(captor.capture());
        CompanyVersion opened = captor.getValue();
        assertThat(opened.getEffectiveFrom()).isEqualTo(newFrom);
        assertThat(opened.getEffectiveTo()).isNull();
    }

    @Test
    void updateProfileRejectsBackdatedWindow() {
        Company company = existingCompany();
        CompanyVersion current = new CompanyVersion();
        current.setCompany(company);
        current.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        current.setEffectiveTo(null);

        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(versionRepository.findByCompanyAndEffectiveToIsNull(company))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(
                        () ->
                                service.updateProfile(
                                        company.getId(),
                                        new UpdateCompanyProfileRequest(
                                                profile(LocalDate.of(2026, 3, 1)))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void setStatusFlipsActiveFlag() {
        Company company = existingCompany();
        company.setActive(true);
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));

        CompanyResponse resp = service.setStatus(company.getId(), false);
        assertThat(resp.active()).isFalse();
        assertThat(company.isActive()).isFalse();
    }

    @Test
    void getByIdReturns404WhenMissing() {
        UUID id = UUID.randomUUID();
        when(companyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id, null))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void softDeleteDelegatesToRepository() {
        Company company = existingCompany();
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));

        service.softDelete(company.getId());
        verify(companyRepository).delete(company);
    }

    // --- fixtures -----------------------------------------------------------

    private Company existingCompany() {
        Company c = new Company();
        c.setId(UUID.randomUUID());
        c.setTenant(tenant);
        c.setCode("ACME");
        c.setActive(true);
        return c;
    }

    private CompanyProfile profile(LocalDate effectiveFrom) {
        return new CompanyProfile(
                "Acme Inc", "Acme Legal Corp", null, "UTC", "USD", 4, effectiveFrom);
    }
}
