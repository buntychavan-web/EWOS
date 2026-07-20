package com.ewos.leave.application;

import com.ewos.leave.api.LeaveMapper;
import com.ewos.leave.api.dto.CreateLeaveTypeRequest;
import com.ewos.leave.api.dto.LeaveTypeResponse;
import com.ewos.leave.api.dto.UpdateLeaveTypeRequest;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveTypeRepository;
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
public class LeaveTypeService {

    private static final String LEAVE_TYPE_CACHE = "leave.type";

    private final LeaveTypeRepository repository;
    private final LeaveMapper mapper;

    public LeaveTypeService(LeaveTypeRepository repository, LeaveMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @CacheEvict(value = LEAVE_TYPE_CACHE, allEntries = true)
    public LeaveTypeResponse create(CreateLeaveTypeRequest request) {
        if (repository.existsByTenantIdAndCodeIgnoreCase(request.tenantId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Leave type code already in use for this tenant");
        }
        LeaveType t = new LeaveType();
        t.setTenantId(request.tenantId());
        t.setCode(request.code());
        t.setName(request.name());
        t.setDescription(request.description());
        if (request.paid() != null) {
            t.setPaid(request.paid());
        }
        if (request.accrualDaysPerYear() != null) {
            t.setAccrualDaysPerYear(request.accrualDaysPerYear());
        }
        if (request.maxBalanceDays() != null) {
            t.setMaxBalanceDays(request.maxBalanceDays());
        }
        if (request.carryForwardDays() != null) {
            t.setCarryForwardDays(request.carryForwardDays());
        }
        if (request.requiresApproval() != null) {
            t.setRequiresApproval(request.requiresApproval());
        }
        if (request.minNoticeDays() != null) {
            t.setMinNoticeDays(request.minNoticeDays());
        }
        if (request.active() != null) {
            t.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            t.setSortOrder(request.sortOrder());
        }
        return mapper.toResponse(repository.save(t));
    }

    @CacheEvict(value = LEAVE_TYPE_CACHE, allEntries = true)
    public LeaveTypeResponse update(UUID tenantId, UUID id, UpdateLeaveTypeRequest request) {
        LeaveType t = require(tenantId, id);
        if (request.name() != null) {
            t.setName(request.name());
        }
        if (request.description() != null) {
            t.setDescription(request.description());
        }
        if (request.paid() != null) {
            t.setPaid(request.paid());
        }
        if (request.accrualDaysPerYear() != null) {
            t.setAccrualDaysPerYear(request.accrualDaysPerYear());
        }
        if (request.maxBalanceDays() != null) {
            t.setMaxBalanceDays(request.maxBalanceDays());
        }
        if (request.carryForwardDays() != null) {
            t.setCarryForwardDays(request.carryForwardDays());
        }
        if (request.requiresApproval() != null) {
            t.setRequiresApproval(request.requiresApproval());
        }
        if (request.minNoticeDays() != null) {
            t.setMinNoticeDays(request.minNoticeDays());
        }
        if (request.active() != null) {
            t.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            t.setSortOrder(request.sortOrder());
        }
        return mapper.toResponse(t);
    }

    @Cacheable(value = LEAVE_TYPE_CACHE, key = "#tenantId + ':' + #id")
    @Transactional(readOnly = true)
    public LeaveTypeResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @CacheEvict(value = LEAVE_TYPE_CACHE, allEntries = true)
    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    LeaveType require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave type not found"));
    }
}
