package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.StatutoryJurisdictionResponse;
import com.ewos.payroll.infrastructure.persistence.StatutoryJurisdictionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only lookup of the seeded country/state jurisdiction master (migration V52). */
@Service
@Transactional(readOnly = true)
public class StatutoryJurisdictionService {

    private final StatutoryJurisdictionRepository repository;
    private final PayrollMapper mapper;

    public StatutoryJurisdictionService(
            StatutoryJurisdictionRepository repository, PayrollMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<StatutoryJurisdictionResponse> forCountry(String countryCode) {
        return repository.findAllByCountryCodeAndActiveTrueOrderByName(countryCode).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
