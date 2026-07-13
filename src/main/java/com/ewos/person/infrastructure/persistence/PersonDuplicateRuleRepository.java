package com.ewos.person.infrastructure.persistence;

import com.ewos.company.domain.Tenant;
import com.ewos.person.domain.PersonDuplicateRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonDuplicateRuleRepository extends JpaRepository<PersonDuplicateRule, UUID> {

    List<PersonDuplicateRule> findByTenantAndEnabledTrueOrderByWeightDesc(Tenant tenant);

    List<PersonDuplicateRule> findByTenant(Tenant tenant);
}
