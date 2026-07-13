package com.ewos.person.infrastructure.persistence;

import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonFamilyMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonFamilyMemberRepository extends JpaRepository<PersonFamilyMember, UUID> {

    List<PersonFamilyMember> findByPerson(Person person);
}
