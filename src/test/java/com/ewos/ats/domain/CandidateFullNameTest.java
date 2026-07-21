package com.ewos.ats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CandidateFullNameTest {

    @Test
    void withoutMiddleName() {
        Candidate c = new Candidate();
        c.setFirstName("Ada");
        c.setLastName("Lovelace");
        assertThat(c.fullName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void withMiddleName() {
        Candidate c = new Candidate();
        c.setFirstName("Ada");
        c.setMiddleName("King");
        c.setLastName("Lovelace");
        assertThat(c.fullName()).isEqualTo("Ada King Lovelace");
    }

    @Test
    void blankMiddleNameSkipped() {
        Candidate c = new Candidate();
        c.setFirstName("Grace");
        c.setMiddleName("   ");
        c.setLastName("Hopper");
        assertThat(c.fullName()).isEqualTo("Grace Hopper");
    }
}
