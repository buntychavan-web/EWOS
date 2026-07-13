package com.ewos.person.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ewos.person.api.dto.ReadinessResponse;
import com.ewos.person.domain.BloodGroup;
import com.ewos.person.domain.Gender;
import com.ewos.person.domain.MaritalStatus;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonContact;
import com.ewos.person.domain.PersonVersion;
import com.ewos.person.infrastructure.persistence.PersonAddressRepository;
import com.ewos.person.infrastructure.persistence.PersonContactRepository;
import com.ewos.person.infrastructure.persistence.PersonEducationRepository;
import com.ewos.person.infrastructure.persistence.PersonEmergencyContactRepository;
import com.ewos.person.infrastructure.persistence.PersonFamilyMemberRepository;
import com.ewos.person.infrastructure.persistence.PersonIdentityDocumentRepository;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import com.ewos.person.infrastructure.persistence.PersonVersionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileReadinessServiceTest {

    @Mock PersonRepository personRepository;
    @Mock PersonVersionRepository versionRepository;
    @Mock PersonContactRepository contactRepository;
    @Mock PersonAddressRepository addressRepository;
    @Mock PersonEmergencyContactRepository emergencyRepository;
    @Mock PersonEducationRepository educationRepository;
    @Mock PersonFamilyMemberRepository familyRepository;
    @Mock PersonIdentityDocumentRepository documentRepository;

    private ProfileReadinessService service;
    private Person person;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId(UUID.randomUUID());
        lenient().when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));
        lenient().when(addressRepository.findByPerson(person)).thenReturn(List.of());
        lenient()
                .when(emergencyRepository.findByPersonOrderByPriorityAsc(person))
                .thenReturn(List.of());
        lenient().when(educationRepository.findByPerson(person)).thenReturn(List.of());
        lenient().when(familyRepository.findByPerson(person)).thenReturn(List.of());
        lenient().when(documentRepository.findByPerson(person)).thenReturn(List.of());

        service =
                new ProfileReadinessService(
                        personRepository,
                        versionRepository,
                        contactRepository,
                        addressRepository,
                        emergencyRepository,
                        educationRepository,
                        familyRepository,
                        documentRepository);
    }

    @Test
    void allEmptyReturnsZero() {
        when(versionRepository.findByPersonAndEffectiveToIsNull(person))
                .thenReturn(Optional.empty());
        when(contactRepository.findByPersonAndEffectiveToIsNull(person))
                .thenReturn(Optional.empty());
        ReadinessResponse r = service.compute(person.getId());
        assertThat(r.basicPct()).isZero();
        assertThat(r.contactPct()).isZero();
        assertThat(r.overallPct()).isZero();
    }

    @Test
    void fullyPopulatedReturnsHundred() {
        PersonVersion v = new PersonVersion();
        v.setFirstName("Alice");
        v.setLastName("Wong");
        v.setGender(Gender.FEMALE);
        v.setDateOfBirth(LocalDate.of(1990, 1, 1));
        v.setMaritalStatus(MaritalStatus.MARRIED);
        v.setBloodGroup(BloodGroup.O_POS);
        v.setNationality("IN");
        when(versionRepository.findByPersonAndEffectiveToIsNull(person)).thenReturn(Optional.of(v));

        PersonContact c = new PersonContact();
        c.setPersonalMobile("+911234500000");
        c.setPersonalEmail("alice@example.com");
        when(contactRepository.findByPersonAndEffectiveToIsNull(person)).thenReturn(Optional.of(c));

        when(addressRepository.findByPerson(person))
                .thenReturn(List.of(new com.ewos.person.domain.PersonAddress()));
        when(emergencyRepository.findByPersonOrderByPriorityAsc(person))
                .thenReturn(List.of(new com.ewos.person.domain.PersonEmergencyContact()));
        when(educationRepository.findByPerson(person))
                .thenReturn(List.of(new com.ewos.person.domain.PersonEducation()));
        when(familyRepository.findByPerson(person))
                .thenReturn(List.of(new com.ewos.person.domain.PersonFamilyMember()));
        when(documentRepository.findByPerson(person))
                .thenReturn(List.of(new com.ewos.person.domain.PersonIdentityDocument()));

        ReadinessResponse r = service.compute(person.getId());
        assertThat(r.basicPct()).isEqualTo(100);
        assertThat(r.contactPct()).isEqualTo(100);
        assertThat(r.overallPct()).isEqualTo(100);
    }

    @Test
    void partialBasicScales() {
        PersonVersion v = new PersonVersion();
        v.setFirstName("Alice");
        v.setLastName("Wong");
        when(versionRepository.findByPersonAndEffectiveToIsNull(person)).thenReturn(Optional.of(v));
        when(contactRepository.findByPersonAndEffectiveToIsNull(person))
                .thenReturn(Optional.empty());

        ReadinessResponse r = service.compute(person.getId());
        // 2 out of 7 basic fields
        assertThat(r.basicPct()).isEqualTo(28);
        assertThat(r.contactPct()).isZero();
    }
}
