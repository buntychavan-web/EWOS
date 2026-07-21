package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CompensationLineRequest;
import com.ewos.payroll.api.dto.CreateEmployeeCompensationRequest;
import com.ewos.payroll.api.dto.EmployeeCompensationResponse;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.EmployeeCompensationLine;
import com.ewos.payroll.domain.PayComponent;
import com.ewos.payroll.domain.PayGroup;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.EmployeeCompensationRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages effective-dated compensation records per employee. Creating a new record supersedes the
 * previously active record for that employee (previous.active = false, previous.effectiveTo = new
 * record's effectiveFrom - 1 day if not already set).
 */
@Service
@Transactional
public class EmployeeCompensationService {

    private final EmployeeCompensationRepository repository;
    private final EmployeeRepository employees;
    private final PayComponentService components;
    private final PayGroupService payGroups;
    private final PayrollMapper mapper;
    private final ApplicationEventPublisher events;

    public EmployeeCompensationService(
            EmployeeCompensationRepository repository,
            EmployeeRepository employees,
            PayComponentService components,
            PayGroupService payGroups,
            PayrollMapper mapper,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.employees = employees;
        this.components = components;
        this.payGroups = payGroups;
        this.mapper = mapper;
        this.events = events;
    }

    public EmployeeCompensationResponse create(CreateEmployeeCompensationRequest request) {
        Employee employee =
                employees
                        .findByIdAndTenantId(request.employeeId(), request.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee belongs to a different company");
        }

        // Supersede the currently-active compensation for this employee, if any.
        repository
                .findActiveForEmployee(request.tenantId(), request.employeeId())
                .ifPresent(
                        existing -> {
                            existing.setActive(false);
                            if (existing.getEffectiveTo() == null) {
                                existing.setEffectiveTo(request.effectiveFrom().minusDays(1));
                            }
                        });

        PayGroup group = null;
        if (request.payGroupId() != null) {
            group = payGroups.require(request.tenantId(), request.payGroupId());
            if (!group.getCompanyId().equals(request.companyId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Pay group belongs to a different company");
            }
        }

        EmployeeCompensation c = new EmployeeCompensation();
        c.setTenantId(request.tenantId());
        c.setCompanyId(request.companyId());
        c.setEmployee(employee);
        c.setPayGroup(group);
        c.setEffectiveFrom(request.effectiveFrom());
        c.setEffectiveTo(request.effectiveTo());
        c.setFrequency(request.frequency());
        c.setBasicSalary(request.basicSalary());
        c.setCurrency(request.currency());
        c.setNotes(request.notes());
        c.setActive(true);
        EmployeeCompensation saved = repository.save(c);

        if (request.lines() != null) {
            for (CompensationLineRequest lineReq : request.lines()) {
                PayComponent component =
                        components.require(request.tenantId(), lineReq.payComponentId());
                EmployeeCompensationLine line = new EmployeeCompensationLine();
                line.setPayComponent(component);
                line.setAmount(lineReq.amount() != null ? lineReq.amount() : BigDecimal.ZERO);
                line.setPercentage(
                        lineReq.percentage() != null ? lineReq.percentage() : BigDecimal.ZERO);
                saved.addLine(line);
            }
        }

        publish(PayrollEventType.COMPENSATION_CHANGED, saved);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public EmployeeCompensationResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompensationResponse> historyForEmployee(UUID tenantId, UUID employeeId) {
        return repository.findHistoryForEmployee(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeCompensation requireActiveForEmployee(UUID tenantId, UUID employeeId) {
        return repository
                .findActiveForEmployee(tenantId, employeeId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "No active compensation for employee " + employeeId));
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompensation> activeForCompany(UUID tenantId, UUID companyId) {
        return repository.findActiveForCompany(tenantId, companyId);
    }

    /** Active compensations restricted to a set of employees; used by supplementary/F&F runs. */
    @Transactional(readOnly = true)
    public List<EmployeeCompensation> activeForEmployeeIds(
            UUID tenantId, java.util.Collection<UUID> employeeIds) {
        return repository.findActiveForEmployeeIds(tenantId, employeeIds);
    }

    public EmployeeCompensation require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Employee compensation not found"));
    }

    private void publish(PayrollEventType type, EmployeeCompensation c) {
        events.publishEvent(
                new PayrollEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        null,
                        null,
                        null,
                        null,
                        c.getEmployee() != null ? c.getEmployee().getId() : null,
                        c.getBasicSalary(),
                        currentActor(),
                        Instant.now()));
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
