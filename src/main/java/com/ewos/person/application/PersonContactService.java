package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.person.api.dto.ContactResponse;
import com.ewos.person.api.dto.SetContactRequest;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonContact;
import com.ewos.person.infrastructure.persistence.PersonContactRepository;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonContactService {

    private final PersonContactRepository contactRepository;
    private final PersonRepository personRepository;

    public PersonContactService(
            PersonContactRepository contactRepository, PersonRepository personRepository) {
        this.contactRepository = contactRepository;
        this.personRepository = personRepository;
    }

    public ContactResponse setContact(UUID personId, SetContactRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), null);
        Person person = requirePerson(personId);
        contactRepository
                .findByPersonAndEffectiveToIsNull(person)
                .ifPresent(
                        current -> {
                            LocalDate closeAt = req.effectiveFrom().minusDays(1);
                            if (closeAt.isBefore(current.getEffectiveFrom())) {
                                throw new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "New contact effectiveFrom would overlap the current row");
                            }
                            current.setEffectiveTo(closeAt);
                        });
        PersonContact c = new PersonContact();
        c.setPerson(person);
        c.setPersonalMobile(req.personalMobile());
        c.setAlternateMobile(req.alternateMobile());
        c.setPersonalEmail(req.personalEmail());
        c.setAlternateEmail(req.alternateEmail());
        c.setEffectiveFrom(req.effectiveFrom());
        c.setEffectiveTo(null);
        return PersonMapper.toContact(contactRepository.save(c));
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> list(UUID personId) {
        Person person = requirePerson(personId);
        return contactRepository.findByPersonOrderByEffectiveFromDesc(person).stream()
                .map(PersonMapper::toContact)
                .toList();
    }

    private Person requirePerson(UUID id) {
        return personRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));
    }
}
