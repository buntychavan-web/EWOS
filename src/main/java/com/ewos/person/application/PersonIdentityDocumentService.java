package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.person.api.dto.AddIdentityDocumentRequest;
import com.ewos.person.api.dto.IdentityDocumentResponse;
import com.ewos.person.domain.IdentityDocumentKind;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonIdentityDocument;
import com.ewos.person.infrastructure.persistence.PersonIdentityDocumentRepository;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonIdentityDocumentService {

    private final PersonIdentityDocumentRepository repository;
    private final PersonRepository personRepository;

    public PersonIdentityDocumentService(
            PersonIdentityDocumentRepository repository, PersonRepository personRepository) {
        this.repository = repository;
        this.personRepository = personRepository;
    }

    public IdentityDocumentResponse add(UUID personId, AddIdentityDocumentRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), req.effectiveTo());
        Person person = requirePerson(personId);

        // PAN + Aadhaar are nationally unique across live rows.
        if (req.documentKind() == IdentityDocumentKind.PAN
                || req.documentKind() == IdentityDocumentKind.AADHAAR) {
            repository
                    .findByDocumentKindAndDocumentNumber(req.documentKind(), req.documentNumber())
                    .ifPresent(
                            existing -> {
                                throw new ApiException(
                                        HttpStatus.CONFLICT,
                                        req.documentKind()
                                                + " "
                                                + req.documentNumber()
                                                + " is already recorded for another person");
                            });
        }

        PersonIdentityDocument d = new PersonIdentityDocument();
        d.setPerson(person);
        d.setDocumentKind(req.documentKind());
        d.setDocumentNumber(req.documentNumber());
        d.setIssuedBy(req.issuedBy());
        d.setIssuedOn(req.issuedOn());
        d.setExpiresOn(req.expiresOn());
        d.setDocumentUrl(req.documentUrl());
        d.setEffectiveFrom(req.effectiveFrom());
        d.setEffectiveTo(req.effectiveTo());
        d.setVerified(req.verified());
        return PersonMapper.toDocument(repository.save(d));
    }

    public IdentityDocumentResponse retire(UUID personId, UUID id, LocalDate effectiveTo) {
        PersonIdentityDocument d =
                repository
                        .findById(id)
                        .filter(x -> x.getPerson().getId().equals(personId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Identity document not found for person"));
        EffectiveDateValidator.requireOrdered(d.getEffectiveFrom(), effectiveTo);
        d.setEffectiveTo(effectiveTo);
        return PersonMapper.toDocument(d);
    }

    public void delete(UUID personId, UUID id) {
        PersonIdentityDocument d =
                repository
                        .findById(id)
                        .filter(x -> x.getPerson().getId().equals(personId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Identity document not found for person"));
        repository.delete(d);
    }

    @Transactional(readOnly = true)
    public List<IdentityDocumentResponse> list(UUID personId, IdentityDocumentKind kind) {
        Person person = requirePerson(personId);
        List<PersonIdentityDocument> rows =
                kind == null
                        ? repository.findByPerson(person)
                        : repository.findByPersonAndDocumentKind(person, kind);
        return rows.stream().map(PersonMapper::toDocument).toList();
    }

    private Person requirePerson(UUID id) {
        return personRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));
    }
}
