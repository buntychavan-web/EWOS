package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.person.api.dto.AddEmergencyContactRequest;
import com.ewos.person.api.dto.EmergencyContactResponse;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonEmergencyContact;
import com.ewos.person.infrastructure.persistence.PersonEmergencyContactRepository;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonEmergencyContactService {

    private final PersonEmergencyContactRepository repository;
    private final PersonRepository personRepository;

    public PersonEmergencyContactService(
            PersonEmergencyContactRepository repository, PersonRepository personRepository) {
        this.repository = repository;
        this.personRepository = personRepository;
    }

    public EmergencyContactResponse add(UUID personId, AddEmergencyContactRequest req) {
        Person person = requirePerson(personId);
        PersonEmergencyContact e = new PersonEmergencyContact();
        e.setPerson(person);
        e.setName(req.name());
        e.setRelationship(req.relationship());
        e.setPriority(req.priority());
        e.setMobile(req.mobile());
        e.setAlternateMobile(req.alternateMobile());
        e.setEmail(req.email());
        e.setAddress(req.address());
        return PersonMapper.toEmergency(repository.save(e));
    }

    public void delete(UUID personId, UUID contactId) {
        PersonEmergencyContact e =
                repository
                        .findById(contactId)
                        .filter(x -> x.getPerson().getId().equals(personId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Emergency contact not found for person"));
        repository.delete(e);
    }

    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> list(UUID personId) {
        Person person = requirePerson(personId);
        return repository.findByPersonOrderByPriorityAsc(person).stream()
                .map(PersonMapper::toEmergency)
                .toList();
    }

    private Person requirePerson(UUID id) {
        return personRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));
    }
}
