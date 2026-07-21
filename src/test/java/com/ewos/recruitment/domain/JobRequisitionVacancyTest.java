package com.ewos.recruitment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobRequisitionVacancyTest {

    @Test
    void hasVacancyWhenFilledLessThanHeadcount() {
        JobRequisition r = new JobRequisition();
        r.setHeadcount(3);
        r.setFilledCount(2);
        assertThat(r.hasVacancy()).isTrue();
    }

    @Test
    void noVacancyWhenFilledEqualsHeadcount() {
        JobRequisition r = new JobRequisition();
        r.setHeadcount(2);
        r.setFilledCount(2);
        assertThat(r.hasVacancy()).isFalse();
    }
}
