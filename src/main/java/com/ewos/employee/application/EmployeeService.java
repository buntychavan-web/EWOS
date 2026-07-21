package com.ewos.employee.application;

import com.ewos.employee.api.EmployeeMapper;
import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.EmployeeSearchCriteria;
import com.ewos.employee.api.dto.HireEmployeeRequest;
import com.ewos.employee.api.dto.TerminateEmployeeRequest;
import com.ewos.employee.api.dto.UpdateEmployeeRequest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeLifecyclePolicy;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.domain.EmploymentType;
import com.ewos.employee.domain.events.EmployeeEvent;
import com.ewos.employee.domain.events.EmployeeEventType;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.employee.infrastructure.persistence.EmployeeSpecifications;
import com.ewos.employee.infrastructure.persistence.EmploymentTypeRepository;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmployeeService {

    private static final String EMPLOYEE_CACHE = "employee.byId";

    private final EmployeeRepository employees;
    private final EmploymentTypeRepository employmentTypes;
    private final OrganizationUnitRepository orgUnits;
    private final EmployeeLifecyclePolicy policy;
    private final EmployeeMapper mapper;
    private final ApplicationEventPublisher events;

    public EmployeeService(
            EmployeeRepository employees,
            EmploymentTypeRepository employmentTypes,
            OrganizationUnitRepository orgUnits,
            EmployeeLifecyclePolicy policy,
            EmployeeMapper mapper,
            ApplicationEventPublisher events) {
        this.employees = employees;
        this.employmentTypes = employmentTypes;
        this.orgUnits = orgUnits;
        this.policy = policy;
        this.mapper = mapper;
        this.events = events;
    }

    public EmployeeResponse hire(HireEmployeeRequest request) {
        if (employees.existsByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                request.tenantId(), request.companyId(), request.employeeNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Employee number already in use for this tenant + company");
        }
        if (employees.existsByTenantIdAndCompanyIdAndWorkEmailIgnoreCase(
                request.tenantId(), request.companyId(), request.workEmail())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Work email already in use for this tenant + company");
        }

        Employee e = new Employee();
        e.setTenantId(request.tenantId());
        e.setCompanyId(request.companyId());
        e.setPersonId(request.personId());
        e.setEmployeeNumber(request.employeeNumber());
        e.setFirstName(request.firstName());
        e.setMiddleName(request.middleName());
        e.setLastName(request.lastName());
        e.setDisplayName(
                request.displayName() != null
                        ? request.displayName()
                        : buildDisplayName(request.firstName(), request.lastName()));
        e.setWorkEmail(request.workEmail());
        e.setPersonalEmail(request.personalEmail());
        e.setPhone(request.phone());
        e.setDateOfBirth(request.dateOfBirth());
        e.setGenderCode(request.genderCode());
        e.setHireDate(request.hireDate());
        e.setStatus(EmployeeStatus.ACTIVE);

        if (request.employmentTypeId() != null) {
            e.setEmploymentType(requireType(request.tenantId(), request.employmentTypeId()));
        }
        if (request.primaryOrgUnitId() != null) {
            e.setPrimaryOrgUnit(requireOrgUnit(request.tenantId(), request.primaryOrgUnitId()));
        }
        if (request.managerEmployeeId() != null) {
            Employee mgr = require(request.tenantId(), request.managerEmployeeId());
            policy.assertValidManager(e, mgr);
            e.setManager(mgr);
        }

        Employee saved = employees.save(e);
        publish(EmployeeEventType.HIRED, saved, saved.getHireDate());
        return mapper.toResponse(saved);
    }

    @CacheEvict(value = EMPLOYEE_CACHE, key = "#tenantId + ':' + #id")
    public EmployeeResponse update(UUID tenantId, UUID id, UpdateEmployeeRequest request) {
        Employee e = require(tenantId, id);
        policy.assertMutable(e);

        boolean reassignedOrgUnit = false;
        boolean reassignedManager = false;

        if (request.firstName() != null) {
            e.setFirstName(request.firstName());
        }
        if (request.middleName() != null) {
            e.setMiddleName(request.middleName());
        }
        if (request.lastName() != null) {
            e.setLastName(request.lastName());
        }
        if (request.displayName() != null) {
            e.setDisplayName(request.displayName());
        }
        if (request.workEmail() != null
                && !request.workEmail().equalsIgnoreCase(e.getWorkEmail())) {
            if (employees.existsByTenantIdAndCompanyIdAndWorkEmailIgnoreCase(
                    e.getTenantId(), e.getCompanyId(), request.workEmail())) {
                throw new ApiException(HttpStatus.CONFLICT, "Work email already in use");
            }
            e.setWorkEmail(request.workEmail());
        }
        if (request.personalEmail() != null) {
            e.setPersonalEmail(request.personalEmail());
        }
        if (request.phone() != null) {
            e.setPhone(request.phone());
        }
        if (request.dateOfBirth() != null) {
            e.setDateOfBirth(request.dateOfBirth());
        }
        if (request.genderCode() != null) {
            e.setGenderCode(request.genderCode());
        }
        if (request.employmentTypeId() != null
                && (e.getEmploymentType() == null
                        || !e.getEmploymentType().getId().equals(request.employmentTypeId()))) {
            e.setEmploymentType(requireType(tenantId, request.employmentTypeId()));
        }
        if (request.primaryOrgUnitId() != null
                && (e.getPrimaryOrgUnit() == null
                        || !e.getPrimaryOrgUnit().getId().equals(request.primaryOrgUnitId()))) {
            e.setPrimaryOrgUnit(requireOrgUnit(tenantId, request.primaryOrgUnitId()));
            reassignedOrgUnit = true;
        }
        if (request.managerEmployeeId() != null
                && (e.getManager() == null
                        || !e.getManager().getId().equals(request.managerEmployeeId()))) {
            Employee mgr = require(tenantId, request.managerEmployeeId());
            policy.assertValidManager(e, mgr);
            e.setManager(mgr);
            reassignedManager = true;
        }

        EmployeeEventType eventType;
        if (reassignedManager) {
            eventType = EmployeeEventType.REASSIGNED_MANAGER;
        } else if (reassignedOrgUnit) {
            eventType = EmployeeEventType.REASSIGNED_ORG_UNIT;
        } else {
            eventType = EmployeeEventType.UPDATED;
        }
        publish(eventType, e, null);
        return mapper.toResponse(e);
    }

    @CacheEvict(value = EMPLOYEE_CACHE, key = "#tenantId + ':' + #id")
    public EmployeeResponse changeStatus(UUID tenantId, UUID id, EmployeeStatus target) {
        Employee e = require(tenantId, id);
        if (e.getStatus() == target) {
            return mapper.toResponse(e);
        }
        if (target == EmployeeStatus.TERMINATED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Use POST /employees/{id}/terminate to terminate; requires a termination date");
        }
        policy.assertMutable(e);
        e.setStatus(target);
        publish(EmployeeEventType.STATUS_CHANGED, e, null);
        return mapper.toResponse(e);
    }

    @CacheEvict(value = EMPLOYEE_CACHE, key = "#tenantId + ':' + #id")
    public EmployeeResponse terminate(UUID tenantId, UUID id, TerminateEmployeeRequest request) {
        Employee e = require(tenantId, id);
        policy.assertTerminationAllowed(e, request.terminationDate());
        e.setStatus(EmployeeStatus.TERMINATED);
        e.setTerminationDate(request.terminationDate());
        publish(EmployeeEventType.TERMINATED, e, request.terminationDate());
        return mapper.toResponse(e);
    }

    @CacheEvict(value = EMPLOYEE_CACHE, key = "#tenantId + ':' + #id")
    public void delete(UUID tenantId, UUID id) {
        Employee e = require(tenantId, id);
        long reports = employees.countDirectReports(id);
        if (reports > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Employee has " + reports + " direct reports; reassign them first");
        }
        employees.delete(e);
        publish(EmployeeEventType.DELETED, e, null);
    }

    @Cacheable(value = EMPLOYEE_CACHE, key = "#tenantId + ':' + #id")
    @Transactional(readOnly = true)
    public EmployeeResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> search(EmployeeSearchCriteria criteria, Pageable pageable) {
        return employees
                .findAll(EmployeeSpecifications.matching(criteria), pageable)
                .map(mapper::toResponse);
    }

    private Employee require(UUID tenantId, UUID id) {
        return employees
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private EmploymentType requireType(UUID tenantId, UUID id) {
        return employmentTypes
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.BAD_REQUEST, "Employment type not found"));
    }

    private OrganizationUnit requireOrgUnit(UUID tenantId, UUID id) {
        return orgUnits.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.BAD_REQUEST, "Organization unit not found"));
    }

    private static String buildDisplayName(String first, String last) {
        return (first + " " + last).trim();
    }

    private void publish(EmployeeEventType type, Employee e, java.time.LocalDate effectiveDate) {
        events.publishEvent(
                new EmployeeEvent(
                        type,
                        e.getId(),
                        e.getTenantId(),
                        e.getCompanyId(),
                        e.getPersonId(),
                        e.getPrimaryOrgUnit() != null ? e.getPrimaryOrgUnit().getId() : null,
                        e.getManager() != null ? e.getManager().getId() : null,
                        e.getEmployeeNumber(),
                        e.getStatus(),
                        effectiveDate,
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
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
