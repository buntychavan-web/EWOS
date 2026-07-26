package com.ewos.employee.application;

import com.ewos.employee.api.EmployeeMapper;
import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.LinkUserRequest;
import com.ewos.employee.api.dto.ProvisionUserRequest;
import com.ewos.employee.api.dto.UnlinkUserRequest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeIdentityLinkAction;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.identity.api.dto.CreateUserRequest;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.application.UserService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 1.3 — admin-triggered actions linking an {@code Employee} to a {@code User} login. Linking is
 * deliberately admin-only, never self-service: an employee claiming their own record from inside the app
 * would be an impersonation risk with no reliable way to verify the claim (see the Sprint 1.3 SDD, §6.2).
 */
@Service
@Transactional
public class EmployeeIdentityLinkService {

    private final EmployeeRepository employees;
    private final ClientAccessGuard guard;
    private final EmployeeMapper mapper;
    private final EmployeeIdentityLinkHistoryRecorder historyRecorder;
    private final UserService userService;

    public EmployeeIdentityLinkService(
            EmployeeRepository employees,
            ClientAccessGuard guard,
            EmployeeMapper mapper,
            EmployeeIdentityLinkHistoryRecorder historyRecorder,
            UserService userService) {
        this.employees = employees;
        this.guard = guard;
        this.mapper = mapper;
        this.historyRecorder = historyRecorder;
        this.userService = userService;
    }

    public EmployeeResponse linkUser(UUID tenantId, UUID employeeId, LinkUserRequest request) {
        Employee e = require(tenantId, employeeId);
        guard.requireAccessForCompany(e.getCompanyId());
        if (employees.existsByCompanyIdAndUserId(e.getCompanyId(), request.userId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "This login is already linked to an employee in this company");
        }
        UUID previousUserId = e.getUserId();
        e.setUserId(request.userId());
        historyRecorder.record(
                e, EmployeeIdentityLinkAction.LINK, previousUserId, request.userId(), request.reason());
        return mapper.toResponse(e);
    }

    public EmployeeResponse unlinkUser(UUID tenantId, UUID employeeId, UnlinkUserRequest request) {
        Employee e = require(tenantId, employeeId);
        guard.requireAccessForCompany(e.getCompanyId());
        UUID previousUserId = e.getUserId();
        if (previousUserId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Employee has no linked login to unlink");
        }
        e.setUserId(null);
        historyRecorder.record(
                e, EmployeeIdentityLinkAction.UNLINK, previousUserId, null, request.reason());
        return mapper.toResponse(e);
    }

    public EmployeeResponse provisionUser(UUID tenantId, UUID employeeId, ProvisionUserRequest request) {
        Employee e = require(tenantId, employeeId);
        guard.requireAccessForCompany(e.getCompanyId());
        if (e.getUserId() != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Employee already has a linked login; unlink it first");
        }
        UserResponse created =
                userService.create(
                        new CreateUserRequest(
                                request.username(),
                                request.email(),
                                request.password(),
                                request.roleIds(),
                                request.enabled()));
        e.setUserId(created.id());
        historyRecorder.record(
                e, EmployeeIdentityLinkAction.PROVISION, null, created.id(), request.reason());
        return mapper.toResponse(e);
    }

    private Employee require(UUID tenantId, UUID id) {
        return employees
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
    }
}
