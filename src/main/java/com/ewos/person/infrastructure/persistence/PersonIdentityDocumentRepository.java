package com.ewos.person.infrastructure.persistence;

import com.ewos.person.domain.IdentityDocumentKind;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonIdentityDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonIdentityDocumentRepository
        extends JpaRepository<PersonIdentityDocument, UUID> {

    List<PersonIdentityDocument> findByPerson(Person person);

    List<PersonIdentityDocument> findByPersonAndDocumentKind(
            Person person, IdentityDocumentKind kind);

    Optional<PersonIdentityDocument> findByDocumentKindAndDocumentNumber(
            IdentityDocumentKind kind, String documentNumber);

    List<PersonIdentityDocument> findByDocumentNumber(String documentNumber);
}
