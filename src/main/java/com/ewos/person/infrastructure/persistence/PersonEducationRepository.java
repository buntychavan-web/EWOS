package com.ewos.person.infrastructure.persistence;

import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonEducation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonEducationRepository extends JpaRepository<PersonEducation, UUID> {

    List<PersonEducation> findByPerson(Person person);
}
