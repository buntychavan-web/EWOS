package com.ewos.person.application;

import com.ewos.person.api.dto.PersonResponse;
import com.ewos.person.domain.IdentityDocumentKind;
import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonContact;
import com.ewos.person.domain.PersonIdentityDocument;
import com.ewos.person.domain.PersonVersion;
import com.ewos.person.infrastructure.persistence.PersonContactRepository;
import com.ewos.person.infrastructure.persistence.PersonIdentityDocumentRepository;
import com.ewos.person.infrastructure.persistence.PersonVersionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fast search across Person ID / name / mobile / email / PAN / Passport / Aadhaar. Returns unique
 * persons; each row hits its own index in the DB.
 */
@Service
@Transactional(readOnly = true)
public class PersonSearchService {

    private final PersonContactRepository contactRepository;
    private final PersonIdentityDocumentRepository documentRepository;
    private final PersonVersionRepository versionRepository;

    public PersonSearchService(
            PersonContactRepository contactRepository,
            PersonIdentityDocumentRepository documentRepository,
            PersonVersionRepository versionRepository) {
        this.contactRepository = contactRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
    }

    public List<PersonResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.trim();
        Map<UUID, PersonResponse> hits = new LinkedHashMap<>();

        // Mobile
        for (PersonContact c : contactRepository.findByPersonalMobile(q)) {
            addPerson(hits, c.getPerson());
        }
        // Email
        for (PersonContact c : contactRepository.findByPersonalEmailIgnoreCase(q)) {
            addPerson(hits, c.getPerson());
        }
        // Documents (PAN / Aadhaar / Passport / any number)
        for (IdentityDocumentKind kind :
                new IdentityDocumentKind[] {
                    IdentityDocumentKind.PAN,
                    IdentityDocumentKind.AADHAAR,
                    IdentityDocumentKind.PASSPORT
                }) {
            documentRepository
                    .findByDocumentKindAndDocumentNumber(kind, q)
                    .ifPresent(d -> addPerson(hits, d.getPerson()));
        }
        for (PersonIdentityDocument d : documentRepository.findByDocumentNumber(q)) {
            addPerson(hits, d.getPerson());
        }
        // Name substring against open versions.
        String lower = q.toLowerCase(Locale.ROOT);
        for (PersonVersion v : versionRepository.findAll()) {
            if (v.getEffectiveTo() != null) {
                continue;
            }
            String full =
                    (safe(v.getFirstName()) + " " + safe(v.getLastName())).toLowerCase(Locale.ROOT);
            if (full.contains(lower)) {
                addPerson(hits, v.getPerson());
            }
        }
        // Group Person ID exact match.
        for (PersonVersion v : versionRepository.findAll()) {
            if (v.getEffectiveTo() != null) {
                continue;
            }
            if (v.getPerson().getGroupPersonId().equalsIgnoreCase(q)) {
                addPerson(hits, v.getPerson());
            }
        }

        return new ArrayList<>(hits.values());
    }

    private void addPerson(Map<UUID, PersonResponse> acc, Person p) {
        if (acc.containsKey(p.getId())) {
            return;
        }
        PersonVersion current = versionRepository.findByPersonAndEffectiveToIsNull(p).orElse(null);
        acc.put(p.getId(), PersonMapper.toPerson(p, current));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
