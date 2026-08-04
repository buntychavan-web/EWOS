package com.ewos.attendance.application;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.CreateHolidayRequest;
import com.ewos.attendance.api.dto.HolidayResponse;
import com.ewos.attendance.domain.Holiday;
import com.ewos.attendance.infrastructure.persistence.HolidayRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sprint 24L item 3 — CRUD for the holiday calendar consumed by {@code AttendanceLopService}. */
@Service
@Transactional
public class HolidayService {

    private final HolidayRepository repository;
    private final AttendanceMapper mapper;
    private final ClientAccessGuard guard;

    public HolidayService(
            HolidayRepository repository, AttendanceMapper mapper, ClientAccessGuard guard) {
        this.repository = repository;
        this.mapper = mapper;
        this.guard = guard;
    }

    public HolidayResponse create(CreateHolidayRequest request) {
        if (request.companyId() != null) {
            guard.requireAccessForCompany(request.companyId());
        }
        if (repository
                .findExact(request.tenantId(), request.companyId(), request.holidayDate())
                .isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "A holiday already exists for this tenant/company on " + request.holidayDate());
        }
        Holiday h = new Holiday();
        h.setTenantId(request.tenantId());
        h.setCompanyId(request.companyId());
        h.setHolidayDate(request.holidayDate());
        h.setName(request.name());
        h.setRecurringAnnually(request.recurringAnnually());
        return mapper.toResponse(repository.save(h));
    }

    @Transactional(readOnly = true)
    public HolidayResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderByHolidayDateAsc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> forCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return repository.findEffectiveForCompany(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        Holiday h = require(tenantId, id);
        if (h.getCompanyId() != null) {
            guard.requireAccessForCompany(h.getCompanyId());
        }
        repository.delete(h);
    }

    private Holiday require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Holiday not found"));
    }
}
