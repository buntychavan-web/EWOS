package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.person.api.dto.CreatePersonRequest;
import com.ewos.person.api.dto.DuplicateCheckRequest;
import com.ewos.person.api.dto.DuplicateCheckResponse;
import com.ewos.person.api.dto.PersonProfile;
import com.ewos.person.api.dto.PersonResponse;
import com.ewos.person.api.dto.PersonVersionResponse;
import com.ewos.person.api.dto.UpdatePersonProfileRequest;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonVersion;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import com.ewos.person.infrastructure.persistence.PersonSpecifications;
import com.ewos.person.infrastructure.persistence.PersonVersionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Person lifecycle: create (with duplicate detection), read, update-profile, status, delete. */
@Service
@Transactional
public class PersonService {

    private final PersonRepository personRepository;
    private final PersonVersionRepository versionRepository;
    private final PersonIdGenerator idGenerator;
    private final TenantResolver tenantResolver;
    private final DuplicateDetectionService duplicateDetectionService;

    public PersonService(
            PersonRepository personRepository,
            PersonVersionRepository versionRepository,
            PersonIdGenerator idGenerator,
            TenantResolver tenantResolver,
            DuplicateDetectionService duplicateDetectionService) {
        this.personRepository = personRepository;
        this.versionRepository = versionRepository;
        this.idGenerator = idGenerator;
        this.tenantResolver = tenantResolver;
        this.duplicateDetectionService = duplicateDetectionService;
    }

    public PersonResponse create(CreatePersonRequest request) {
        PersonProfile profile = request.profile();
        EffectiveDateValidator.requireOrdered(profile.effectiveFrom(), null);
        Tenant tenant = tenantResolver.resolve(request.tenantId());

        // Duplicate detection — never silently create duplicates. The caller can override with
        // PERSON_DUPLICATE_OVERRIDE.
        DuplicateCheckResponse matches =
                duplicateDetectionService.check(
                        new DuplicateCheckRequest(
                                tenant.getId(),
                                profile.firstName(),
                                profile.lastName(),
                                profile.dateOfBirth(),
                                null,
                                null,
                                null,
                                null,
                                null));
        duplicateDetectionService.assertOverrideAllowedIfDuplicates(
                matches, request.overrideDuplicates(), hasAuthority("PERSON_DUPLICATE_OVERRIDE"));

        Person person = new Person();
        person.setTenant(tenant);
        person.setGroupPersonId(idGenerator.nextId());
        person.setActive(true);
        Person saved = personRepository.save(person);

        PersonVersion initial = openNewVersion(saved, profile, 1, null);
        return PersonMapper.toPerson(saved, initial);
    }

    public PersonResponse updateProfile(UUID personId, UpdatePersonProfileRequest request) {
        Person person = requirePerson(personId);
        PersonProfile profile = request.profile();
        EffectiveDateValidator.requireOrdered(profile.effectiveFrom(), null);

        // Close previous open version at newFrom - 1.
        int nextNumber = versionRepository.findMaxVersionNumber(person).orElse(0) + 1;
        versionRepository
                .findByPersonAndEffectiveToIsNull(person)
                .ifPresent(
                        current -> {
                            LocalDate closeAt = profile.effectiveFrom().minusDays(1);
                            if (closeAt.isBefore(current.getEffectiveFrom())) {
                                throw new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "New profile effectiveFrom ("
                                                + profile.effectiveFrom()
                                                + ") would overlap the current version starting "
                                                + current.getEffectiveFrom());
                            }
                            current.setEffectiveTo(closeAt);
                        });

        PersonVersion next = openNewVersion(person, profile, nextNumber, request.approvedBy());
        return PersonMapper.toPerson(person, next);
    }

    public PersonResponse setStatus(UUID personId, boolean active) {
        Person person = requirePerson(personId);
        person.setActive(active);
        PersonVersion current =
                versionRepository.findByPersonAndEffectiveToIsNull(person).orElse(null);
        return PersonMapper.toPerson(person, current);
    }

    public void softDelete(UUID personId) {
        Person person = requirePerson(personId);
        personRepository.delete(person);
    }

    @Transactional(readOnly = true)
    public PersonResponse getById(UUID personId, LocalDate asOf) {
        Person person = requirePerson(personId);
        PersonVersion version =
                asOf == null
                        ? versionRepository.findByPersonAndEffectiveToIsNull(person).orElse(null)
                        : versionRepository.findEffectiveAt(person, asOf).stream()
                                .findFirst()
                                .orElse(null);
        return PersonMapper.toPerson(person, version);
    }

    @Transactional(readOnly = true)
    public PersonResponse getByGroupPersonId(String groupPersonId) {
        Person person =
                personRepository
                        .findByGroupPersonId(groupPersonId.toUpperCase(Locale.ROOT))
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));
        PersonVersion current =
                versionRepository.findByPersonAndEffectiveToIsNull(person).orElse(null);
        return PersonMapper.toPerson(person, current);
    }

    @Transactional(readOnly = true)
    public List<PersonVersionResponse> listVersions(UUID personId) {
        Person person = requirePerson(personId);
        return versionRepository.findByPersonOrderByVersionNumberDesc(person).stream()
                .map(PersonMapper::toVersion)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PersonResponse> search(
            UUID tenantId, String groupPersonId, Boolean active, Pageable pageable) {
        return personRepository
                .findAll(PersonSpecifications.matching(tenantId, groupPersonId, active), pageable)
                .map(
                        p -> {
                            PersonVersion current =
                                    versionRepository
                                            .findByPersonAndEffectiveToIsNull(p)
                                            .orElse(null);
                            return PersonMapper.toPerson(p, current);
                        });
    }

    // --- helpers ------------------------------------------------------------

    Person requirePerson(UUID id) {
        return personRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Person not found"));
    }

    private PersonVersion openNewVersion(
            Person person, PersonProfile profile, int versionNumber, UUID approvedBy) {
        PersonVersion v = new PersonVersion();
        v.setPerson(person);
        v.setVersionNumber(versionNumber);
        v.setEffectiveFrom(profile.effectiveFrom());
        v.setEffectiveTo(null);
        v.setFirstName(profile.firstName());
        v.setMiddleName(profile.middleName());
        v.setLastName(profile.lastName());
        v.setPreferredName(profile.preferredName());
        v.setGender(profile.gender());
        v.setDateOfBirth(profile.dateOfBirth());
        v.setMaritalStatus(profile.maritalStatus());
        v.setBloodGroup(profile.bloodGroup());
        v.setNationality(profile.nationality());
        v.setPhotoUrl(profile.photoUrl());
        v.setChangeReason(profile.changeReason());
        v.setApprovedBy(approvedBy);
        return versionRepository.save(v);
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (authority.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
