package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.person.api.dto.AddEducationRequest;
import com.ewos.person.api.dto.EducationResponse;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonEducation;
import com.ewos.person.infrastructure.persistence.PersonEducationRepository;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonEducationService {

    private final PersonEducationRepository repository;
    private final PersonRepository personRepository;

    public PersonEducationService(
            PersonEducationRepository repository, PersonRepository personRepository) {
        this.repository = repository;
        this.personRepository = personRepository;
    }

    public EducationResponse add(UUID personId, AddEducationRequest req) {
        Person person = requirePerson(personId);
        PersonEducation e = new PersonEducation();
        e.setPerson(person);
        e.setQualification(req.qualification());
        e.setInstitution(req.institution());
        e.setPassingYear(req.passingYear());
        e.setGrade(req.grade());
        e.setSpecialization(req.specialization());
        e.setDocumentUrl(req.documentUrl());
        return PersonMapper.toEducation(repository.save(e));
    }

    public void delete(UUID personId, UUID educationId) {
        PersonEducation e =
                repository
                        .findById(educationId)
                        .filter(x -> x.getPerson().getId().equals(personId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Education record not found for person"));
        repository.delete(e);
    }

    @Transactional(readOnly = true)
    public List<EducationResponse> list(UUID personId) {
        Person person = requirePerson(personId);
        return repository.findByPerson(person).stream().map(PersonMapper::toEducation).toList();
    }

    private Person requirePerson(UUID id) {
        return personRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));
    }
}
