package com.ewos.attendance.application;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.AttendancePolicyResponse;
import com.ewos.attendance.api.dto.CreateAttendancePolicyRequest;
import com.ewos.attendance.api.dto.UpdateAttendancePolicyRequest;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.infrastructure.persistence.AttendancePolicyRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AttendancePolicyService {

    private static final String POLICY_CACHE = "attendance.policy";

    private final AttendancePolicyRepository repository;
    private final AttendanceMapper mapper;

    public AttendancePolicyService(AttendancePolicyRepository repository, AttendanceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @CacheEvict(value = POLICY_CACHE, allEntries = true)
    public AttendancePolicyResponse create(CreateAttendancePolicyRequest request) {
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
        return mapper.toResponse(repository.save(p));
    }

    @CacheEvict(value = POLICY_CACHE, allEntries = true)
    public AttendancePolicyResponse update(
            UUID tenantId, UUID id, UpdateAttendancePolicyRequest request) {
        AttendancePolicy p = require(tenantId, id);
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
        return mapper.toResponse(p);
    }

    @Cacheable(value = POLICY_CACHE, key = "#tenantId + ':' + #id")
    @Transactional(readOnly = true)
    public AttendancePolicyResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<AttendancePolicyResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @CacheEvict(value = POLICY_CACHE, allEntries = true)
    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
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
        List<AttendancePolicy> candidates = repository.findEffectiveForCompany(tenantId, companyId);
        if (candidates.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "No active attendance policy for tenant + company; create one first");
        }
        return candidates.get(0);
    }
}
