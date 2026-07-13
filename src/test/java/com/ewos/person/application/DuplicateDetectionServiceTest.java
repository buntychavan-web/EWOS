package com.ewos.person.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.company.infrastructure.persistence.TenantRepository;
import com.ewos.person.api.dto.DuplicateCheckRequest;
import com.ewos.person.api.dto.DuplicateCheckResponse;
import com.ewos.person.api.dto.DuplicateMatch;
import com.ewos.person.domain.DuplicateRuleKind;
import com.ewos.person.domain.IdentityDocumentKind;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonContact;
import com.ewos.person.domain.PersonDuplicateRule;
import com.ewos.person.domain.PersonIdentityDocument;
import com.ewos.person.domain.PersonVersion;
import com.ewos.person.infrastructure.persistence.PersonContactRepository;
import com.ewos.person.infrastructure.persistence.PersonDuplicateRuleRepository;
import com.ewos.person.infrastructure.persistence.PersonIdentityDocumentRepository;
import com.ewos.person.infrastructure.persistence.PersonVersionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    @Mock PersonDuplicateRuleRepository ruleRepository;
    @Mock PersonIdentityDocumentRepository documentRepository;
    @Mock PersonContactRepository contactRepository;
    @Mock PersonVersionRepository versionRepository;
    @Mock TenantRepository tenantRepository;

    private DuplicateDetectionService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setCode("DEFAULT");
        lenient().when(tenantRepository.findByCode("DEFAULT")).thenReturn(Optional.of(tenant));
        lenient().when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        service =
                new DuplicateDetectionService(
                        ruleRepository,
                        documentRepository,
                        contactRepository,
                        versionRepository,
                        new TenantResolver(tenantRepository));
    }

    @Test
    void panRuleReturnsMatchWhenNumberFound() {
        Person p = person("P000000001", "Alice", "Wong");
        PersonIdentityDocument pan = new PersonIdentityDocument();
        pan.setPerson(p);
        pan.setDocumentKind(IdentityDocumentKind.PAN);
        pan.setDocumentNumber("ABCDE1234F");

        when(ruleRepository.findByTenantAndEnabledTrueOrderByWeightDesc(tenant))
                .thenReturn(List.of(rule(DuplicateRuleKind.PAN, 100)));
        when(documentRepository.findByDocumentKindAndDocumentNumber(
                        IdentityDocumentKind.PAN, "ABCDE1234F"))
                .thenReturn(Optional.of(pan));

        DuplicateCheckResponse resp =
                service.check(
                        new DuplicateCheckRequest(
                                null, null, null, null, null, null, "ABCDE1234F", null, null));

        assertThat(resp.matches()).hasSize(1);
        assertThat(resp.matches().get(0).ruleKind()).isEqualTo(DuplicateRuleKind.PAN);
        assertThat(resp.matches().get(0).personId()).isEqualTo(p.getId());
    }

    @Test
    void emptyRulesReturnEmpty() {
        when(ruleRepository.findByTenantAndEnabledTrueOrderByWeightDesc(tenant))
                .thenReturn(List.of());
        DuplicateCheckResponse resp =
                service.check(
                        new DuplicateCheckRequest(
                                null, "A", "B", null, "9990", "a@b.com", "X", "Y", "Z"));
        assertThat(resp.matches()).isEmpty();
    }

    @Test
    void mobileRuleMatchesPersonContact() {
        Person p = person("P000000002", "Bob", "Kim");
        PersonContact contact = new PersonContact();
        contact.setPerson(p);
        contact.setPersonalMobile("+911234500000");

        when(ruleRepository.findByTenantAndEnabledTrueOrderByWeightDesc(tenant))
                .thenReturn(List.of(rule(DuplicateRuleKind.MOBILE, 70)));
        when(contactRepository.findByPersonalMobile("+911234500000")).thenReturn(List.of(contact));

        DuplicateCheckResponse resp =
                service.check(
                        new DuplicateCheckRequest(
                                null, null, null, null, "+911234500000", null, null, null, null));

        assertThat(resp.matches()).hasSize(1);
        assertThat(resp.matches().get(0).ruleKind()).isEqualTo(DuplicateRuleKind.MOBILE);
    }

    @Test
    void assertOverrideThrows409WhenNotAcknowledged() {
        DuplicateCheckResponse matches =
                new DuplicateCheckResponse(
                        List.of(
                                new DuplicateMatch(
                                        UUID.randomUUID(),
                                        "P000000001",
                                        "Alice",
                                        DuplicateRuleKind.PAN,
                                        "ABCDE1234F",
                                        100)));

        assertThatThrownBy(() -> service.assertOverrideAllowedIfDuplicates(matches, false, true))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void assertOverrideThrows403WhenAcknowledgedWithoutAuthority() {
        DuplicateCheckResponse matches =
                new DuplicateCheckResponse(
                        List.of(
                                new DuplicateMatch(
                                        UUID.randomUUID(),
                                        "P000000001",
                                        "Alice",
                                        DuplicateRuleKind.PAN,
                                        "ABCDE1234F",
                                        100)));

        assertThatThrownBy(() -> service.assertOverrideAllowedIfDuplicates(matches, true, false))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void assertOverridePassesWhenAcknowledgedWithAuthority() {
        DuplicateCheckResponse matches =
                new DuplicateCheckResponse(
                        List.of(
                                new DuplicateMatch(
                                        UUID.randomUUID(),
                                        "P000000001",
                                        "Alice",
                                        DuplicateRuleKind.PAN,
                                        "ABCDE1234F",
                                        100)));
        // Should not throw
        service.assertOverrideAllowedIfDuplicates(matches, true, true);
    }

    private Person person(String groupPersonId, String firstName, String lastName) {
        Person p = new Person();
        p.setId(UUID.randomUUID());
        p.setTenant(tenant);
        p.setGroupPersonId(groupPersonId);
        // Provide an open version for the "fullName" helper.
        PersonVersion v = new PersonVersion();
        v.setPerson(p);
        v.setFirstName(firstName);
        v.setLastName(lastName);
        v.setEffectiveTo(null);
        lenient()
                .when(versionRepository.findByPersonAndEffectiveToIsNull(p))
                .thenReturn(Optional.of(v));
        return p;
    }

    private PersonDuplicateRule rule(DuplicateRuleKind kind, int weight) {
        PersonDuplicateRule r = new PersonDuplicateRule();
        r.setId(UUID.randomUUID());
        r.setTenant(tenant);
        r.setRuleKind(kind);
        r.setEnabled(true);
        r.setWeight(weight);
        return r;
    }
}
