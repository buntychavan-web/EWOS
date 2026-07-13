package com.ewos.company.infrastructure.persistence;

import com.ewos.company.domain.Company;
import com.ewos.company.domain.StatutoryRegistration;
import com.ewos.company.domain.StatutoryRegistrationKind;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatutoryRegistrationRepository
        extends JpaRepository<StatutoryRegistration, UUID> {

    List<StatutoryRegistration> findByCompany(Company company);

    List<StatutoryRegistration> findByCompanyAndKind(
            Company company, StatutoryRegistrationKind kind);

    boolean existsByKindAndRegistrationNumber(
            StatutoryRegistrationKind kind, String registrationNumber);
}
