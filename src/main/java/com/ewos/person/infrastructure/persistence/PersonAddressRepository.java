package com.ewos.person.infrastructure.persistence;

import com.ewos.person.domain.AddressKind;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonAddress;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonAddressRepository extends JpaRepository<PersonAddress, UUID> {

    List<PersonAddress> findByPerson(Person person);

    List<PersonAddress> findByPersonAndAddressKind(Person person, AddressKind kind);
}
