package com.ewos.person.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.company.infrastructure.persistence.TenantRepository;
import com.ewos.person.api.dto.CreatePersonRequest;
import com.ewos.person.api.dto.DuplicateCheckResponse;
import com.ewos.person.api.dto.DuplicateMatch;
import com.ewos.person.api.dto.PersonProfile;
import com.ewos.person.api.dto.PersonResponse;
import com.ewos.person.api.dto.UpdatePersonProfileRequest;
import com.ewos.person.domain.DuplicateRuleKind;
import com.ewos.person.domain.Gender;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonVersion;
import com.ewos.person.infrastructure.persistence.PersonRepository;
import com.ewos.person.infrastructure.persistence.PersonVersionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock PersonRepository personRepository;
    @Mock PersonVersionRepository versionRepository;
    @Mock PersonIdGenerator idGenerator;
    @Mock TenantRepository tenantRepository;
    @Mock DuplicateDetectionService duplicateDetectionService;

    private PersonService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setCode("DEFAULT");
        lenient().when(tenantRepository.findByCode("DEFAULT")).thenReturn(Optional.of(tenant));
        lenient().when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        lenient().when(idGenerator.nextId()).thenReturn("P000000001");
        lenient()
                .when(duplicateDetectionService.check(any()))
                .thenReturn(new DuplicateCheckResponse(List.of()));

        lenient()
                .when(personRepository.save(any(Person.class)))
                .thenAnswer(
                        inv -> {
                            Person p = inv.getArgument(0);
                            if (p.getId() == null) {
                                p.setId(UUID.randomUUID());
                            }
                            return p;
                        });
        lenient()
                .when(versionRepository.save(any(PersonVersion.class)))
                .thenAnswer(
                        inv -> {
                            PersonVersion v = inv.getArgument(0);
                            if (v.getId() == null) {
                                v.setId(UUID.randomUUID());
                            }
                            return v;
                        });

        service =
                new PersonService(
                        personRepository,
                        versionRepository,
                        idGenerator,
                        new TenantResolver(tenantRepository),
                        duplicateDetectionService);
    }

    @Test
    void createIssuesGroupPersonIdAndOpenVersion() {
        CreatePersonRequest req = new CreatePersonRequest(null, profile("Alice", "Wong"), false);

        PersonResponse resp = service.create(req);

        assertThat(resp.groupPersonId()).isEqualTo("P000000001");
        assertThat(resp.active()).isTrue();
        assertThat(resp.currentVersion().versionNumber()).isEqualTo(1);
        assertThat(resp.currentVersion().effectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        verify(versionRepository).save(any(PersonVersion.class));
    }

    @Test
    void createRejectsUnacknowledgedDuplicates() {
        when(duplicateDetectionService.check(any()))
                .thenReturn(
                        new DuplicateCheckResponse(
                                List.of(
                                        new DuplicateMatch(
                                                UUID.randomUUID(),
                                                "P000000010",
                                                "Alice Wong",
                                                DuplicateRuleKind.NAME_DOB,
                                                "Alice Wong / 1990-01-01",
                                                50))));
        org.mockito.Mockito.doThrow(
                        new ApiException(
                                HttpStatus.CONFLICT,
                                "Possible duplicates detected — resubmit with overrideDuplicates=true"
                                        + " to force creation"))
                .when(duplicateDetectionService)
                .assertOverrideAllowedIfDuplicates(
                        any(),
                        org.mockito.ArgumentMatchers.eq(false),
                        org.mockito.ArgumentMatchers.anyBoolean());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePersonRequest(
                                                null, profile("Alice", "Wong"), false)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateProfileClosesPreviousAndBumpsVersionNumber() {
        Person person = existingPerson();
        PersonVersion current = new PersonVersion();
        current.setPerson(person);
        current.setVersionNumber(1);
        current.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        current.setEffectiveTo(null);

        when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));
        when(versionRepository.findByPersonAndEffectiveToIsNull(person))
                .thenReturn(Optional.of(current));
        when(versionRepository.findMaxVersionNumber(person)).thenReturn(Optional.of(1));

        LocalDate newFrom = LocalDate.of(2026, 4, 1);
        service.updateProfile(
                person.getId(),
                new UpdatePersonProfileRequest(profileAt("Alice", "Wong", newFrom), null));

        assertThat(current.getEffectiveTo()).isEqualTo(newFrom.minusDays(1));

        ArgumentCaptor<PersonVersion> captor = ArgumentCaptor.forClass(PersonVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(2);
        assertThat(captor.getValue().getEffectiveFrom()).isEqualTo(newFrom);
    }

    @Test
    void updateProfileRejectsBackdatedWindow() {
        Person person = existingPerson();
        PersonVersion current = new PersonVersion();
        current.setPerson(person);
        current.setVersionNumber(1);
        current.setEffectiveFrom(LocalDate.of(2026, 6, 1));

        when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));
        when(versionRepository.findByPersonAndEffectiveToIsNull(person))
                .thenReturn(Optional.of(current));
        when(versionRepository.findMaxVersionNumber(person)).thenReturn(Optional.of(1));

        assertThatThrownBy(
                        () ->
                                service.updateProfile(
                                        person.getId(),
                                        new UpdatePersonProfileRequest(
                                                profileAt(
                                                        "Alice", "Wong", LocalDate.of(2026, 3, 1)),
                                                null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void setStatusFlipsActive() {
        Person person = existingPerson();
        person.setActive(true);
        when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));

        PersonResponse resp = service.setStatus(person.getId(), false);
        assertThat(resp.active()).isFalse();
        assertThat(person.isActive()).isFalse();
    }

    @Test
    void getByIdMissingReturns404() {
        UUID id = UUID.randomUUID();
        when(personRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id, null))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Person existingPerson() {
        Person p = new Person();
        p.setId(UUID.randomUUID());
        p.setTenant(tenant);
        p.setGroupPersonId("P000000001");
        p.setActive(true);
        return p;
    }

    private PersonProfile profile(String firstName, String lastName) {
        return profileAt(firstName, lastName, LocalDate.of(2026, 1, 1));
    }

    private PersonProfile profileAt(String firstName, String lastName, LocalDate from) {
        return new PersonProfile(
                firstName,
                null,
                lastName,
                null,
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                null,
                null,
                "IN",
                null,
                from,
                "initial");
    }
}
