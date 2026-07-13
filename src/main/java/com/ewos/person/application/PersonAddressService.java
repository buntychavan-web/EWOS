package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.person.api.dto.AddAddressRequest;
import com.ewos.person.api.dto.AddressResponse;
import com.ewos.person.domain.AddressKind;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonAddress;
import com.ewos.person.infrastructure.persistence.PersonAddressRepository;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonAddressService {

    private final PersonAddressRepository addressRepository;
    private final PersonRepository personRepository;

    public PersonAddressService(
            PersonAddressRepository addressRepository, PersonRepository personRepository) {
        this.addressRepository = addressRepository;
        this.personRepository = personRepository;
    }

    public AddressResponse add(UUID personId, AddAddressRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), req.effectiveTo());
        Person person = requirePerson(personId);
        PersonAddress a = new PersonAddress();
        a.setPerson(person);
        a.setAddressKind(req.addressKind());
        a.setLine1(req.line1());
        a.setLine2(req.line2());
        a.setCity(req.city());
        a.setState(req.state());
        a.setCountry(req.country());
        a.setPostalCode(req.postalCode());
        a.setEffectiveFrom(req.effectiveFrom());
        a.setEffectiveTo(req.effectiveTo());
        return PersonMapper.toAddress(addressRepository.save(a));
    }

    public AddressResponse retire(UUID personId, UUID addressId, LocalDate effectiveTo) {
        PersonAddress a =
                addressRepository
                        .findById(addressId)
                        .filter(x -> x.getPerson().getId().equals(personId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Address not found for person"));
        EffectiveDateValidator.requireOrdered(a.getEffectiveFrom(), effectiveTo);
        a.setEffectiveTo(effectiveTo);
        return PersonMapper.toAddress(a);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID personId, AddressKind kind) {
        Person person = requirePerson(personId);
        List<PersonAddress> rows =
                kind == null
                        ? addressRepository.findByPerson(person)
                        : addressRepository.findByPersonAndAddressKind(person, kind);
        return rows.stream().map(PersonMapper::toAddress).toList();
    }

    private Person requirePerson(UUID id) {
        return personRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));
    }
}
