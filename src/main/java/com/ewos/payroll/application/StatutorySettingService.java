package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateStatutorySettingRequest;
import com.ewos.payroll.api.dto.StatutorySettingResponse;
import com.ewos.payroll.domain.StatutorySetting;
import com.ewos.payroll.infrastructure.persistence.StatutorySettingRepository;
import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Effective-dated statutory KV store. {@link #resolveNumeric(UUID, String, String, LocalDate)} is
 * what the payroll calculator uses at run time to look up a rate on a given as-of date.
 */
@Service
@Transactional
public class StatutorySettingService {

    private final StatutorySettingRepository repository;
    private final PayrollMapper mapper;

    public StatutorySettingService(StatutorySettingRepository repository, PayrollMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public StatutorySettingResponse create(CreateStatutorySettingRequest request) {
        if (request.valueNumeric() == null
                && (request.valueString() == null || request.valueString().isBlank())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Either valueNumeric or valueString must be provided");
        }
        StatutorySetting s = new StatutorySetting();
        s.setTenantId(request.tenantId());
        s.setJurisdiction(request.jurisdiction());
        s.setCode(request.code());
        s.setName(request.name());
        s.setDescription(request.description());
        s.setValueNumeric(request.valueNumeric());
        s.setValueString(request.valueString());
        s.setEffectiveFrom(request.effectiveFrom());
        s.setEffectiveTo(request.effectiveTo());
        if (request.active() != null) {
            s.setActive(request.active());
        }
        return mapper.toResponse(repository.save(s));
    }

    @Transactional(readOnly = true)
    public StatutorySettingResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<StatutorySettingResponse> listByJurisdiction(UUID tenantId, String jurisdiction) {
        return repository
                .findAllByTenantIdAndJurisdictionOrderByCodeAscEffectiveFromDesc(
                        tenantId, jurisdiction)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Returns the effective numeric value or empty if no active row covers the date. */
    @Transactional(readOnly = true)
    public Optional<java.math.BigDecimal> resolveNumeric(
            UUID tenantId, String jurisdiction, String code, LocalDate on) {
        return repository.findEffective(tenantId, jurisdiction, code, on).stream()
                .findFirst()
                .map(StatutorySetting::getValueNumeric);
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    public StatutorySetting require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Statutory setting not found"));
    }
}
