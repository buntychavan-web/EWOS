package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.dto.CreateBusinessUnitRequest;
import com.ewos.payroll.api.dto.CreateCostCentreRequest;
import com.ewos.payroll.api.dto.CreateGLMappingRequest;
import com.ewos.payroll.domain.AllocationDimension;
import com.ewos.payroll.domain.BusinessUnit;
import com.ewos.payroll.domain.CostCentre;
import com.ewos.payroll.domain.GLAccount;
import com.ewos.payroll.domain.GLAccountType;
import com.ewos.payroll.domain.GLMapping;
import com.ewos.payroll.domain.GLMappingSourceKind;
import com.ewos.payroll.infrastructure.persistence.BusinessUnitRepository;
import com.ewos.payroll.infrastructure.persistence.CostCentreRepository;
import com.ewos.payroll.infrastructure.persistence.GLAccountRepository;
import com.ewos.payroll.infrastructure.persistence.GLMappingRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * GL configuration CRUD: cost centre / business unit code-uniqueness per company (the dimensions
 * {@link EmployeeCostAllocationService} and the journal generator split expense lines across), plus
 * the GL account/mapping catalogue that backs statutory and pay-component routing.
 */
@ExtendWith(MockitoExtension.class)
class GlConfigServiceTest {

    @Mock CostCentreRepository costCentres;
    @Mock BusinessUnitRepository businessUnits;
    @Mock GLAccountRepository accounts;
    @Mock GLMappingRepository mappings;
    @Mock ClientAccessGuard guard;

    private GlConfigService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GlConfigService(costCentres, businessUnits, accounts, mappings, guard);
        org.mockito.Mockito.lenient()
                .when(costCentres.save(any(CostCentre.class)))
                .thenAnswer(
                        inv -> {
                            CostCentre c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
        org.mockito.Mockito.lenient()
                .when(businessUnits.save(any(BusinessUnit.class)))
                .thenAnswer(
                        inv -> {
                            BusinessUnit b = inv.getArgument(0);
                            if (b.getId() == null) {
                                b.setId(UUID.randomUUID());
                            }
                            return b;
                        });
        org.mockito.Mockito.lenient()
                .when(mappings.save(any(GLMapping.class)))
                .thenAnswer(
                        inv -> {
                            GLMapping m = inv.getArgument(0);
                            if (m.getId() == null) {
                                m.setId(UUID.randomUUID());
                            }
                            return m;
                        });
    }

    // --- cost centres ---

    @Test
    void createCostCentreRejectsADuplicateCodeWithinTheSameCompany() {
        when(costCentres.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, companyId, "CC-ENG"))
                .thenReturn(true);

        CreateCostCentreRequest req =
                new CreateCostCentreRequest(
                        tenantId, companyId, "CC-ENG", "Engineering", null, true);

        assertThatThrownBy(() -> service.createCostCentre(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createCostCentrePersistsTheGivenFields() {
        when(costCentres.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, companyId, "CC-ENG"))
                .thenReturn(false);

        var response =
                service.createCostCentre(
                        new CreateCostCentreRequest(
                                tenantId,
                                companyId,
                                "CC-ENG",
                                "Engineering",
                                "R&D cost centre",
                                true));

        assertThat(response.code()).isEqualTo("CC-ENG");
        assertThat(response.name()).isEqualTo("Engineering");
    }

    @Test
    void deleteCostCentreThrowsNotFoundForAnUnknownId() {
        UUID id = UUID.randomUUID();
        when(costCentres.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCostCentre(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCostCentreChecksAccessForItsCompanyBeforeDeleting() {
        UUID id = UUID.randomUUID();
        CostCentre c = new CostCentre();
        c.setId(id);
        c.setCompanyId(companyId);
        when(costCentres.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(c));

        service.deleteCostCentre(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
        verify(costCentres).delete(c);
    }

    // --- business units ---

    @Test
    void createBusinessUnitRejectsADuplicateCodeWithinTheSameCompany() {
        when(businessUnits.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, companyId, "BU-SALES"))
                .thenReturn(true);

        CreateBusinessUnitRequest req =
                new CreateBusinessUnitRequest(tenantId, companyId, "BU-SALES", "Sales", null, true);

        assertThatThrownBy(() -> service.createBusinessUnit(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createBusinessUnitAllowsTheSameCodeInADifferentCompany() {
        UUID otherCompany = UUID.randomUUID();
        when(businessUnits.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                        tenantId, otherCompany, "BU-SALES"))
                .thenReturn(false);

        var response =
                service.createBusinessUnit(
                        new CreateBusinessUnitRequest(
                                tenantId, otherCompany, "BU-SALES", "Sales", null, true));

        assertThat(response.code()).isEqualTo("BU-SALES");
        assertThat(response.companyId()).isEqualTo(otherCompany);
    }

    // --- GL mapping ---

    @Test
    void createMappingLinksDebitAndCreditAccountsAndAllocationDimension() {
        UUID debitId = UUID.randomUUID();
        UUID creditId = UUID.randomUUID();
        GLAccount debit = new GLAccount();
        debit.setId(debitId);
        debit.setAccountType(GLAccountType.EXPENSE);
        GLAccount credit = new GLAccount();
        credit.setId(creditId);
        credit.setAccountType(GLAccountType.LIABILITY);
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.PAY_COMPONENT, "BASIC"))
                .thenReturn(Optional.empty());
        when(accounts.findByIdAndTenantId(debitId, tenantId)).thenReturn(Optional.of(debit));
        when(accounts.findByIdAndTenantId(creditId, tenantId)).thenReturn(Optional.of(credit));

        CreateGLMappingRequest req =
                new CreateGLMappingRequest(
                        tenantId,
                        companyId,
                        GLMappingSourceKind.PAY_COMPONENT,
                        "BASIC",
                        debitId,
                        creditId,
                        AllocationDimension.COST_CENTRE,
                        null,
                        true);

        var response = service.createMapping(req);

        assertThat(response.sourceKind()).isEqualTo(GLMappingSourceKind.PAY_COMPONENT);
        assertThat(response.sourceCode()).isEqualTo("BASIC");
        assertThat(response.allocationDimension()).isEqualTo(AllocationDimension.COST_CENTRE);
    }

    @Test
    void createMappingThrowsWhenTheDebitAccountDoesNotExist() {
        UUID debitId = UUID.randomUUID();
        when(mappings.findActive(tenantId, companyId, GLMappingSourceKind.PAY_COMPONENT, "BASIC"))
                .thenReturn(Optional.empty());
        when(accounts.findByIdAndTenantId(debitId, tenantId)).thenReturn(Optional.empty());

        CreateGLMappingRequest req =
                new CreateGLMappingRequest(
                        tenantId,
                        companyId,
                        GLMappingSourceKind.PAY_COMPONENT,
                        "BASIC",
                        debitId,
                        UUID.randomUUID(),
                        AllocationDimension.NONE,
                        null,
                        true);

        assertThatThrownBy(() -> service.createMapping(req)).isInstanceOf(ApiException.class);
    }
}
