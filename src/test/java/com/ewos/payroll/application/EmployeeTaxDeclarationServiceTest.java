package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.api.dto.UpdateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.infrastructure.persistence.EmployeeTaxDeclarationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EmployeeTaxDeclarationServiceTest {

    @Mock EmployeeTaxDeclarationRepository repository;
    @Mock EmployeeRepository employees;
    @Mock com.ewos.payroll.infrastructure.persistence.TaxDeclarationProofRepository proofs;
    @Mock ClientAccessGuard guard;
    private final PayrollMapper mapper = new PayrollMapper();

    private EmployeeTaxDeclarationService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmployeeTaxDeclarationService(repository, employees, proofs, mapper, guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(EmployeeTaxDeclaration.class)))
                .thenAnswer(
                        inv -> {
                            EmployeeTaxDeclaration d = inv.getArgument(0);
                            if (d.getId() == null) {
                                d.setId(UUID.randomUUID());
                            }
                            return d;
                        });
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(companyId);
        return e;
    }

    private CreateEmployeeTaxDeclarationRequest createRequest() {
        return new CreateEmployeeTaxDeclarationRequest(
                tenantId,
                companyId,
                employeeId,
                "2026-27",
                TaxRegime.NEW,
                new BigDecimal("50000"),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void createChecksCompanyAccessBeforeLoadingTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createRejectsADuplicateDeclarationForTheSameEmployeeAndFiscalYear() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(repository.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        tenantId, employeeId, "2026-27"))
                .thenReturn(Optional.of(new EmployeeTaxDeclaration()));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createBuildsADeclarationWiringOnlyTheSuppliedOptionalFields() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(repository.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        tenantId, employeeId, "2026-27"))
                .thenReturn(Optional.empty());

        var response = service.create(createRequest());

        assertThat(response.fiscalYear()).isEqualTo("2026-27");
        assertThat(response.regime()).isEqualTo(TaxRegime.NEW);
        assertThat(response.previousEmployerIncome()).isEqualByComparingTo("50000");
        assertThat(response.otherIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.rentPaidAnnual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.metroCity()).isFalse();
    }

    @Test
    void updateChecksCompanyAccessAndOnlyOverwritesSuppliedFields() {
        EmployeeTaxDeclaration existing = new EmployeeTaxDeclaration();
        existing.setCompanyId(companyId);
        existing.setRentPaidAnnual(new BigDecimal("120000"));
        existing.setMetroCity(true);
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        UpdateEmployeeTaxDeclarationRequest request =
                new UpdateEmployeeTaxDeclarationRequest(
                        TaxRegime.OLD, null, null, null, null, null, null, null);
        var response = service.update(tenantId, id, request);

        verify(guard).requireAccessForCompany(companyId);
        assertThat(response.regime()).isEqualTo(TaxRegime.OLD);
        // Fields not present on the update request must survive unchanged.
        assertThat(response.rentPaidAnnual()).isEqualByComparingTo("120000");
        assertThat(response.metroCity()).isTrue();
    }

    @Test
    void updateThrowsNotFoundForAnUnknownDeclaration() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        tenantId,
                                        id,
                                        new UpdateEmployeeTaxDeclarationRequest(
                                                null, null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownDeclaration() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forEmployeeAndYearThrowsNotFoundWhenNoActiveDeclarationExists() {
        when(repository.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        tenantId, employeeId, "2026-27"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forEmployeeAndYear(tenantId, employeeId, "2026-27"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historyForEmployeeChecksAccessForEveryDistinctCompanyAmongTheResults() {
        EmployeeTaxDeclaration d = new EmployeeTaxDeclaration();
        d.setCompanyId(companyId);
        when(repository.findAllByTenantIdAndEmployeeIdOrderByFiscalYearDesc(tenantId, employeeId))
                .thenReturn(List.of(d));

        service.historyForEmployee(tenantId, employeeId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void deleteChecksCompanyAccessThenDelegatesToTheRepository() {
        EmployeeTaxDeclaration existing = new EmployeeTaxDeclaration();
        existing.setCompanyId(companyId);
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        service.delete(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
        verify(repository).delete(existing);
    }

    @Test
    void uploadProofChecksAccessAndStoresMetadataOnly() {
        EmployeeTaxDeclaration declaration = new EmployeeTaxDeclaration();
        declaration.setCompanyId(companyId);
        UUID declarationId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(declarationId, tenantId))
                .thenReturn(Optional.of(declaration));
        when(proofs.save(any(com.ewos.payroll.domain.TaxDeclarationProof.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response =
                service.uploadProof(
                        tenantId,
                        declarationId,
                        new com.ewos.payroll.api.dto.UploadTaxDeclarationProofRequest(
                                com.ewos.payroll.domain.TaxProofType.RENT_RECEIPT,
                                "receipt.pdf",
                                "application/pdf",
                                1024L,
                                "s3://bucket/receipt.pdf",
                                null));

        verify(guard).requireAccessForCompany(companyId);
        assertThat(response.filename()).isEqualTo("receipt.pdf");
        assertThat(response.storageUri()).isEqualTo("s3://bucket/receipt.pdf");
        assertThat(response.proofType())
                .isEqualTo(com.ewos.payroll.domain.TaxProofType.RENT_RECEIPT);
    }

    @Test
    void proofsForDeclarationChecksAccessAndListsInUploadOrder() {
        EmployeeTaxDeclaration declaration = new EmployeeTaxDeclaration();
        declaration.setCompanyId(companyId);
        UUID declarationId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(declarationId, tenantId))
                .thenReturn(Optional.of(declaration));
        when(proofs.findAllForDeclaration(tenantId, declarationId)).thenReturn(List.of());

        service.proofsForDeclaration(tenantId, declarationId);

        verify(guard).requireAccessForCompany(companyId);
    }
}
