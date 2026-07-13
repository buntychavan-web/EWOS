package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.person.api.dto.ReadinessResponse;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes per-section and overall profile readiness. Missing sections are not blockers — creation
 * is always allowed with a partial profile; this service only surfaces the completion state so the
 * UI can nudge the user.
 */
@Service
@Transactional(readOnly = true)
public class ProfileReadinessService {

    private final PersonRepository personRepository;
    private final PersonVersionRepository versionRepository;
    private final PersonContactRepository contactRepository;
    private final PersonAddressRepository addressRepository;
    private final PersonEmergencyContactRepository emergencyRepository;
    private final PersonEducationRepository educationRepository;
    private final PersonFamilyMemberRepository familyRepository;
    private final PersonIdentityDocumentRepository documentRepository;

    public ProfileReadinessService(
            PersonRepository personRepository,
            PersonVersionRepository versionRepository,
            PersonContactRepository contactRepository,
            PersonAddressRepository addressRepository,
            PersonEmergencyContactRepository emergencyRepository,
            PersonEducationRepository educationRepository,
            PersonFamilyMemberRepository familyRepository,
            PersonIdentityDocumentRepository documentRepository) {
        this.personRepository = personRepository;
        this.versionRepository = versionRepository;
        this.contactRepository = contactRepository;
        this.addressRepository = addressRepository;
        this.emergencyRepository = emergencyRepository;
        this.educationRepository = educationRepository;
        this.familyRepository = familyRepository;
        this.documentRepository = documentRepository;
    }

    public ReadinessResponse compute(UUID personId) {
        Person p =
                personRepository
                        .findById(personId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));

        int basic = basicPct(p);
        int contact = contactPct(p);
        int address = addressRepository.findByPerson(p).isEmpty() ? 0 : 100;
        int emergency = emergencyRepository.findByPersonOrderByPriorityAsc(p).isEmpty() ? 0 : 100;
        int education = educationRepository.findByPerson(p).isEmpty() ? 0 : 100;
        int family = familyRepository.findByPerson(p).isEmpty() ? 0 : 100;
        int documents = documentRepository.findByPerson(p).isEmpty() ? 0 : 100;

        int overall = (basic + contact + address + emergency + education + family + documents) / 7;
        return new ReadinessResponse(
                basic, contact, address, emergency, education, family, documents, overall);
    }

    private int basicPct(Person p) {
        PersonVersion v = versionRepository.findByPersonAndEffectiveToIsNull(p).orElse(null);
        if (v == null) {
            return 0;
        }
        int total = 7;
        int filled = 0;
        if (nonBlank(v.getFirstName())) {
            filled++;
        }
        if (nonBlank(v.getLastName())) {
            filled++;
        }
        if (v.getGender() != null) {
            filled++;
        }
        if (v.getDateOfBirth() != null) {
            filled++;
        }
        if (v.getMaritalStatus() != null) {
            filled++;
        }
        if (v.getBloodGroup() != null) {
            filled++;
        }
        if (nonBlank(v.getNationality())) {
            filled++;
        }
        return filled * 100 / total;
    }

    private int contactPct(Person p) {
        PersonContact c = contactRepository.findByPersonAndEffectiveToIsNull(p).orElse(null);
        if (c == null) {
            return 0;
        }
        int total = 2;
        int filled = 0;
        if (nonBlank(c.getPersonalMobile())) {
            filled++;
        }
        if (nonBlank(c.getPersonalEmail())) {
            filled++;
        }
        return filled * 100 / total;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
