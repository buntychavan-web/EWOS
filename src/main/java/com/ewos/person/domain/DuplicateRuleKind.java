package com.ewos.person.domain;

/**
 * Kinds of duplicate-detection rule the platform supports. Which rules run for a given tenant is
 * data-driven ({@code person_duplicate_rules}); this enum is only the vocabulary.
 */
public enum DuplicateRuleKind {
    PAN,
    AADHAAR,
    PASSPORT,
    MOBILE,
    EMAIL,
    NAME_DOB
}
