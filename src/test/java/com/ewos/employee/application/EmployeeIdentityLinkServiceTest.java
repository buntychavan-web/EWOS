package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.api.EmployeeMapper;
import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.LinkUserRequest;
import com.ewos.employee.api.dto.ProvisionUserRequest;
import com.ewos.employee.api.dto.UnlinkUserRequest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeIdentityLinkAction;
import com.ewos.employee.domain.EmployeeIdentityLinkHistory;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeIdentityLinkHistoryRepository;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.application.UserService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EmployeeIdentityLinkServiceTest {

    @Mock EmployeeRepository employees;
    @Mock ClientAccessGuard guard;
    @Mock EmployeeIdentityLinkHistoryRecorder historyRecorder;
    @Mock EmployeeIdentityLinkHistoryRepository historyRepository;
    @Mock UserService userService;

    private EmployeeIdentityLinkService service;

    @BeforeEach
    void setUp() {
        service =
                new EmployeeIdentityLinkService(
                        employees,
                        guard,
                        new EmployeeMapper(),
                        historyRecorder,
                        historyRepository,
                        userService);
    }

    @Test
    void linkUserSetsUserIdAndRecordsHistory() {
        UUID tenantId = UUID.randomUUID();
        Employee e = employee(tenantId);
        UUID userId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(e.getId(), tenantId)).thenReturn(Optional.of(e));
        when(employees.existsByCompanyIdAndUserId(e.getCompanyId(), userId)).thenReturn(false);

        EmployeeResponse response =
                service.linkUser(tenantId, e.getId(), new LinkUserRequest(userId, "onboarding"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(e.getUserId()).isEqualTo(userId);
        verify(guard).requireAccessForCompany(e.getCompanyId());
        verify(historyRecorder)
                .record(
                        eq(e),
                        eq(EmployeeIdentityLinkAction.LINK),
                        eq(null),
                        eq(userId),
                        eq("onboarding"));
    }

    @Test
    void linkUserRejectsDuplicateActiveLinkInSameCompany() {
        UUID tenantId = UUID.randomUUID();
        Employee e = employee(tenantId);
        UUID userId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(e.getId(), tenantId)).thenReturn(Optional.of(e));
        when(employees.existsByCompanyIdAndUserId(e.getCompanyId(), userId)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.linkUser(
                                        tenantId, e.getId(), new LinkUserRequest(userId, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unlinkUserClearsUserIdAndRecordsHistory() {
        UUID tenantId = UUID.randomUUID();
        Employee e = employee(tenantId);
        UUID previousUserId = UUID.randomUUID();
        e.setUserId(previousUserId);
        when(employees.findByIdAndTenantId(e.getId(), tenantId)).thenReturn(Optional.of(e));

        EmployeeResponse response =
                service.unlinkUser(tenantId, e.getId(), new UnlinkUserRequest("access revoked"));

        assertThat(response.userId()).isNull();
        assertThat(e.getUserId()).isNull();
        verify(historyRecorder)
                .record(
                        eq(e),
                        eq(EmployeeIdentityLinkAction.UNLINK),
                        eq(previousUserId),
                        eq(null),
                        eq("access revoked"));
    }

    @Test
    void unlinkUserRejectsWhenNoLinkExists() {
        UUID tenantId = UUID.randomUUID();
        Employee e = employee(tenantId);
        when(employees.findByIdAndTenantId(e.getId(), tenantId)).thenReturn(Optional.of(e));

        assertThatThrownBy(
                        () -> service.unlinkUser(tenantId, e.getId(), new UnlinkUserRequest(null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void provisionUserCreatesLoginAndLinksIt() {
        UUID tenantId = UUID.randomUUID();
        Employee e = employee(tenantId);
        UUID newUserId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(e.getId(), tenantId)).thenReturn(Optional.of(e));
        when(userService.create(any()))
                .thenReturn(
                        new UserResponse(
                                newUserId,
                                "alice",
                                "alice@ex.com",
                                true,
                                true,
                                Set.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));

        EmployeeResponse response =
                service.provisionUser(
                        tenantId,
                        e.getId(),
                        new ProvisionUserRequest(
                                "alice",
                                "alice@ex.com",
                                "T3mp0rary!",
                                Set.of(),
                                true,
                                "first login"));

        assertThat(response.userId()).isEqualTo(newUserId);
        verify(historyRecorder)
                .record(
                        eq(e),
                        eq(EmployeeIdentityLinkAction.PROVISION),
                        eq(null),
                        eq(newUserId),
                        eq("first login"));
    }

    @Test
    void provisionUserRejectsWhenAlreadyLinked() {
        UUID tenantId = UUID.randomUUID();
        Employee e = employee(tenantId);
        e.setUserId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(e.getId(), tenantId)).thenReturn(Optional.of(e));

        assertThatThrownBy(
                        () ->
                                service.provisionUser(
                                        tenantId,
                                        e.getId(),
                                        new ProvisionUserRequest(
                                                "bob",
                                                "bob@ex.com",
                                                "T3mp0rary!",
                                                Set.of(),
                                                true,
                                                null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void linkUserRejectsUnknownEmployee() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.linkUser(
                                        tenantId,
                                        employeeId,
                                        new LinkUserRequest(UUID.randomUUID(), null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historyOfReturnsRepositoryRowsMappedToResponses() {
        UUID tenantId = UUID.randomUUID();
        Employee e = employee(tenantId);
        when(employees.findByIdAndTenantId(e.getId(), tenantId)).thenReturn(Optional.of(e));
        UUID newUserId = UUID.randomUUID();
        EmployeeIdentityLinkHistory row = new EmployeeIdentityLinkHistory();
        row.setEmployee(e);
        row.setAction(EmployeeIdentityLinkAction.LINK);
        row.setNewUserId(newUserId);
        row.setReason("onboarding");
        when(historyRepository.findAllByEmployeeIdOrderByCreatedAtDesc(e.getId()))
                .thenReturn(List.of(row));

        var result = service.historyOf(tenantId, e.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isEqualTo(EmployeeIdentityLinkAction.LINK);
        assertThat(result.get(0).newUserId()).isEqualTo(newUserId);
        assertThat(result.get(0).reason()).isEqualTo("onboarding");
        verify(guard).requireAccessForCompany(e.getCompanyId());
    }

    @Test
    void historyOfRejectsUnknownEmployee() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.historyOf(tenantId, employeeId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static Employee employee(UUID tenantId) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setCompanyId(UUID.randomUUID());
        e.setEmployeeNumber("EMP-1");
        e.setFirstName("A");
        e.setLastName("B");
        e.setWorkEmail("a@b.com");
        e.setHireDate(LocalDate.now());
        e.setStatus(EmployeeStatus.ACTIVE);
        return e;
    }
}
