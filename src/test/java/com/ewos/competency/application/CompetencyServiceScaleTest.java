package com.ewos.competency.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.competency.api.CompetencyMapper;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.infrastructure.persistence.CompetencyRepository;
import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class CompetencyServiceScaleTest {

    private final CompetencyRepository repo = Mockito.mock(CompetencyRepository.class);
    private final ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);
    private final CompetencyService service =
            new CompetencyService(repo, new CompetencyMapper(), events);

    @Test
    void assertLevelInScale_acceptsBoundary() {
        Competency c = new Competency();
        c.setScaleMin(1);
        c.setScaleMax(5);
        assertThatCode(() -> service.assertLevelInScale(c, 1)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertLevelInScale(c, 5)).doesNotThrowAnyException();
    }

    @Test
    void assertLevelInScale_rejectsBelowMin() {
        Competency c = new Competency();
        c.setScaleMin(1);
        c.setScaleMax(5);
        assertThatThrownBy(() -> service.assertLevelInScale(c, 0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("outside competency scale");
    }

    @Test
    void assertLevelInScale_rejectsAboveMax() {
        Competency c = new Competency();
        c.setScaleMin(1);
        c.setScaleMax(5);
        assertThatThrownBy(() -> service.assertLevelInScale(c, 6)).isInstanceOf(ApiException.class);
    }
}
