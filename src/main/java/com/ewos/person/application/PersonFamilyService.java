package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.person.api.dto.AddFamilyMemberRequest;
import com.ewos.person.api.dto.FamilyMemberResponse;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonFamilyMember;
import com.ewos.person.infrastructure.persistence.PersonFamilyMemberRepository;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonFamilyService {

    private final PersonFamilyMemberRepository repository;
    private final PersonRepository personRepository;

    public PersonFamilyService(
            PersonFamilyMemberRepository repository, PersonRepository personRepository) {
        this.repository = repository;
        this.personRepository = personRepository;
    }

    public FamilyMemberResponse add(UUID personId, AddFamilyMemberRequest req) {
        Person person = requirePerson(personId);
        PersonFamilyMember f = new PersonFamilyMember();
        f.setPerson(person);
        f.setRelation(req.relation());
        f.setName(req.name());
        f.setDateOfBirth(req.dateOfBirth());
        f.setGender(req.gender());
        f.setOccupation(req.occupation());
        f.setDependent(req.dependent());
        f.setMobile(req.mobile());
        f.setEmail(req.email());
        return PersonMapper.toFamily(repository.save(f));
    }

    public void delete(UUID personId, UUID memberId) {
        PersonFamilyMember f =
                repository
                        .findById(memberId)
                        .filter(x -> x.getPerson().getId().equals(personId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Family member not found for person"));
        repository.delete(f);
    }

    @Transactional(readOnly = true)
    public List<FamilyMemberResponse> list(UUID personId) {
        Person person = requirePerson(personId);
        return repository.findByPerson(person).stream().map(PersonMapper::toFamily).toList();
    }

    private Person requirePerson(UUID id) {
        return personRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));
    }
}
