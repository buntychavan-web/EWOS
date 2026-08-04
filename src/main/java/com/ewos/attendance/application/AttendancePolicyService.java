package com.ewos.attendance.application;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.AttendancePolicyResponse;
import com.ewos.attendance.api.dto.CreateAttendancePolicyRequest;
import com.ewos.attendance.api.dto.UpdateAttendancePolicyRequest;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.infrastructure.persistence.AttendancePolicyRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AttendancePolicyService {

    private final AttendancePolicyRepository repository;
    private final AttendanceMapper mapper;
    private final ClientAccessGuard guard;

    public AttendancePolicyService(
            AttendancePolicyRepository repository,
            AttendanceMapper mapper,
            ClientAccessGuard guard) {
        this.repository = repository;
        this.mapper = mapper;
        this.guard = guard;
    }

    public AttendancePolicyResponse create(CreateAttendancePolicyRequest request) {
        if (request.companyId() != null) {
            guard.requireAccessForCompany(request.companyId());
        }
        if (repository.existsByTenantIdAndCodeIgnoreCase(request.tenantId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Attendance policy code already in use for this tenant");
        }
        AttendancePolicy p = new AttendancePolicy();
        p.setTenantId(request.tenantId());
        p.setCompanyId(request.companyId());
        p.setCode(request.code());
        p.setName(request.name());
        p.setDescription(request.description());
        if (request.standardHoursPerDay() != null) {
            p.setStandardHoursPerDay(request.standardHoursPerDay());
        }
        if (request.standardHoursPerWeek() != null) {
            p.setStandardHoursPerWeek(request.standardHoursPerWeek());
        }
        if (request.workingDays() != null) {
            p.setWorkingDays(request.workingDays());
        }
        if (request.graceMinutes() != null) {
            p.setGraceMinutes(request.graceMinutes());
        }
        if (request.overtimeMultiplier() != null) {
            p.setOvertimeMultiplier(request.overtimeMultiplier());
        }
        if (request.periodLengthDays() != null) {
            p.setPeriodLengthDays(request.periodLengthDays());
        }
        if (request.active() != null) {
            p.setActive(request.active());
        }
        if (request.sandwichLeavePolicyEnabled() != null) {
            p.setSandwichLeavePolicyEnabled(request.sandwichLeavePolicyEnabled());
        }
        return mapper.toResponse(repository.save(p));
    }

    public AttendancePolicyResponse update(
            UUID tenantId, UUID id, UpdateAttendancePolicyRequest request) {
        AttendancePolicy p = require(tenantId, id);
        if (p.getCompanyId() != null) {
            guard.requireAccessForCompany(p.getCompanyId());
        }
        if (request.name() != null) {
            p.setName(request.name());
        }
        if (request.description() != null) {
            p.setDescription(request.description());
        }
        if (request.standardHoursPerDay() != null) {
            p.setStandardHoursPerDay(request.standardHoursPerDay());
        }
        if (request.standardHoursPerWeek() != null) {
            p.setStandardHoursPerWeek(request.standardHoursPerWeek());
        }
        if (request.workingDays() != null) {
            p.setWorkingDays(request.workingDays());
        }
        if (request.graceMinutes() != null) {
            p.setGraceMinutes(request.graceMinutes());
        }
        if (request.overtimeMultiplier() != null) {
            p.setOvertimeMultiplier(request.overtimeMultiplier());
        }
        if (request.periodLengthDays() != null) {
            p.setPeriodLengthDays(request.periodLengthDays());
        }
        if (request.active() != null) {
            p.setActive(request.active());
        }
        if (request.sandwichLeavePolicyEnabled() != null) {
            p.setSandwichLeavePolicyEnabled(request.sandwichLeavePolicyEnabled());
        }
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public AttendancePolicyResponse getById(UUID tenantId, UUID id) {
        AttendancePolicy p = require(tenantId, id);
        if (p.getCompanyId() != null) {
            guard.requireAccessForCompany(p.getCompanyId());
        }
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<AttendancePolicyResponse> list(UUID tenantId) {
        List<AttendancePolicy> found = repository.findAllByTenantIdOrderByNameAsc(tenantId);
        guard.requireAccessForCompanies(
                found.stream()
                        .map(AttendancePolicy::getCompanyId)
                        .filter(Objects::nonNull)
                        .toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    public void delete(UUID tenantId, UUID id) {
        AttendancePolicy p = require(tenantId, id);
        if (p.getCompanyId() != null) {
            guard.requireAccessForCompany(p.getCompanyId());
        }
        repository.delete(p);
    }

    /** Package-private lookup used by TimesheetService when opening a period. */
    AttendancePolicy require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Attendance policy not found"));
    }

    /**
     * Resolve the most-specific active policy for a company (company-scoped wins over tenant-scoped
     * fallback).
     */
    @Transactional(readOnly = true)
    public AttendancePolicy effectivePolicyFor(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        List<AttendancePolicy> candidates = repository.findEffectiveForCompany(tenantId, companyId);
        if (candidates.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "No active attendance policy for tenant + company; create one first");
        }
        return candidates.get(0);
    }
}
