package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.ClientAssignment;
import com.ewos.tenancy.domain.Company;
import com.ewos.tenancy.infrastructure.persistence.ClientAssignmentRepository;
import com.ewos.tenancy.infrastructure.persistence.CompanyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ClientAccessGuardTest {

    @Mock ClientAssignmentRepository repository;
    @Mock CompanyRepository companyRepository;

    private ClientAccessGuard guard;

    private void authenticateAs(UUID userId, String... authorities) {
        List<SimpleGrantedAuthority> granted =
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId.toString(), "n/a", granted));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        guard = new ClientAccessGuard(repository, companyRepository);
    }

    @Test
    void unrestrictedAccessWhenHoldingClientAdmin() {
        authenticateAs(UUID.randomUUID(), "CLIENT_ADMIN");
        assertThat(guard.hasUnrestrictedAccess()).isTrue();
    }

    @Test
    void notUnrestrictedWithoutClientAdmin() {
        authenticateAs(UUID.randomUUID(), "CLIENT_READ");
        assertThat(guard.hasUnrestrictedAccess()).isFalse();
    }

    @Test
    void notUnrestrictedWithNoSecurityContext() {
        assertThat(guard.hasUnrestrictedAccess()).isFalse();
    }

    @Test
    void requireAccessPassesForAssignedClient() {
        UUID userId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        authenticateAs(userId, "CLIENT_READ");

        Client client = new Client();
        client.setId(clientId);
        ClientAssignment assignment = new ClientAssignment();
        assignment.setClient(client);
        when(repository.findActiveForUser(
                        org.mockito.ArgumentMatchers.eq(userId),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of(assignment));

        guard.requireAccess(clientId); // no throw
    }

    @Test
    void requireAccessRejectsUnassignedClient() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId, "CLIENT_READ");
        when(repository.findActiveForUser(
                        org.mockito.ArgumentMatchers.eq(userId),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> guard.requireAccess(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void requireAccessBypassedForClientAdmin() {
        authenticateAs(UUID.randomUUID(), "CLIENT_ADMIN");
        guard.requireAccess(UUID.randomUUID()); // no throw, no repository call needed
    }

    @Test
    void accessibleClientIdsEmptyWhenUnauthenticated() {
        assertThat(guard.accessibleClientIds()).isEmpty();
    }

    @Test
    void requireAccessForCompanyPassesWhenResolvedClientIsAssigned() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        authenticateAs(userId, "PAYROLL_READ");

        Client client = new Client();
        client.setId(clientId);
        Company company = new Company();
        company.setId(companyId);
        company.setClient(client);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));

        ClientAssignment assignment = new ClientAssignment();
        assignment.setClient(client);
        when(repository.findActiveForUser(
                        org.mockito.ArgumentMatchers.eq(userId),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of(assignment));

        guard.requireAccessForCompany(companyId); // no throw
    }

    @Test
    void requireAccessForCompanyRejectsWhenClientNotAssigned() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        authenticateAs(userId, "PAYROLL_READ");

        Client client = new Client();
        client.setId(UUID.randomUUID());
        Company company = new Company();
        company.setId(companyId);
        company.setClient(client);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(repository.findActiveForUser(
                        org.mockito.ArgumentMatchers.eq(userId),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> guard.requireAccessForCompany(companyId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void requireAccessForCompanyRejectsWhenCompanyUnknown() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        authenticateAs(userId, "PAYROLL_READ");
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAccessForCompany(companyId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void requireAccessForCompanyBypassedForClientAdmin() {
        authenticateAs(UUID.randomUUID(), "CLIENT_ADMIN");
        guard.requireAccessForCompany(UUID.randomUUID()); // no throw, no repository calls
    }

    @Test
    void requireAccessForCompaniesChecksEachDistinctCompanyOnce() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        authenticateAs(userId, "PAYROLL_READ");

        Client client = new Client();
        client.setId(clientId);
        Company company = new Company();
        company.setId(companyId);
        company.setClient(client);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));

        ClientAssignment assignment = new ClientAssignment();
        assignment.setClient(client);
        when(repository.findActiveForUser(
                        org.mockito.ArgumentMatchers.eq(userId),
                        org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of(assignment));

        guard.requireAccessForCompanies(List.of(companyId, companyId)); // no throw

        org.mockito.Mockito.verify(companyRepository, org.mockito.Mockito.times(1))
                .findById(companyId);
    }

    @Test
    void requireAccessForCompaniesRejectsWhenAnyCompanyIsUnauthorized() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        authenticateAs(userId, "PAYROLL_READ");
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAccessForCompanies(List.of(companyId)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void requireAccessForCompaniesNoOpForEmptyCollection() {
        authenticateAs(UUID.randomUUID(), "PAYROLL_READ");
        guard.requireAccessForCompanies(List.of()); // no throw, no repository calls
    }
}
