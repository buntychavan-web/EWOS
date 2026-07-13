package com.ewos.person.infrastructure.persistence;

import com.ewos.company.domain.Tenant;
import com.ewos.person.domain.Person;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PersonRepository
        extends JpaRepository<Person, UUID>, JpaSpecificationExecutor<Person> {

    Optional<Person> findByGroupPersonId(String groupPersonId);

    boolean existsByTenantAndGroupPersonId(Tenant tenant, String groupPersonId);
}
