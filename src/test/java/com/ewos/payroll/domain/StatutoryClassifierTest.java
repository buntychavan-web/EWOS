package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatutoryClassifierTest {

    private final StatutoryClassifier classifier = new StatutoryClassifier();

    @Test
    void classifiesIndiaPf() {
        var cls = classifier.classify("PF");
        assertThat(cls).isPresent();
        assertThat(cls.get().jurisdiction()).isEqualTo("IN");
        assertThat(cls.get().code()).isEqualTo("PF");
    }

    @Test
    void classifiesUsSocialSecurity() {
        var cls = classifier.classify("SOCIAL_SECURITY");
        assertThat(cls).isPresent();
        assertThat(cls.get().jurisdiction()).isEqualTo("US");
    }

    @Test
    void caseInsensitive() {
        assertThat(classifier.classify("income_tax")).isPresent();
        assertThat(classifier.classify("Income_Tax")).isPresent();
    }

    @Test
    void unknownCodeReturnsEmpty() {
        assertThat(classifier.classify("LOAN_REPAYMENT")).isEmpty();
        assertThat(classifier.classify("HRA")).isEmpty();
        assertThat(classifier.classify(null)).isEmpty();
    }
}
