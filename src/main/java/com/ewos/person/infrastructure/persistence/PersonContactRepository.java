package com.ewos.person.infrastructure.persistence;

import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonContactRepository extends JpaRepository<PersonContact, UUID> {

    Optional<PersonContact> findByPersonAndEffectiveToIsNull(Person person);

    List<PersonContact> findByPersonOrderByEffectiveFromDesc(Person person);

    List<PersonContact> findByPersonalMobile(String mobile);

    List<PersonContact> findByPersonalEmailIgnoreCase(String email);
}
