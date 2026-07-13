package com.ewos.person.infrastructure.persistence;

import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonEmergencyContact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonEmergencyContactRepository
        extends JpaRepository<PersonEmergencyContact, UUID> {

    List<PersonEmergencyContact> findByPersonOrderByPriorityAsc(Person person);
}
