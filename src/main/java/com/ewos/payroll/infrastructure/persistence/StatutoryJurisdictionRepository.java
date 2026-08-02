package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.StatutoryJurisdiction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatutoryJurisdictionRepository
        extends JpaRepository<StatutoryJurisdiction, UUID> {

    Optional<StatutoryJurisdiction> findByCountryCodeAndStateCode(
            String countryCode, String stateCode);

    List<StatutoryJurisdiction> findAllByCountryCodeAndActiveTrueOrderByName(String countryCode);
}
