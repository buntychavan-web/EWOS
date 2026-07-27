package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowGuardEvaluatorTest {

    private final WorkflowGuardEvaluator evaluator = new WorkflowGuardEvaluator();

    @Test
    void blankOrNullGuardAlwaysPasses() {
        assertThat(evaluator.evaluate(null, Map.of())).isTrue();
        assertThat(evaluator.evaluate("", Map.of())).isTrue();
        assertThat(evaluator.evaluate("   ", Map.of())).isTrue();
    }

    @Test
    void trueExpressionPasses() {
        boolean result =
                evaluator.evaluate("#daysRequested <= 2", Map.of("daysRequested", BigDecimal.ONE));
        assertThat(result).isTrue();
    }

    @Test
    void falseExpressionFails() {
        boolean result =
                evaluator.evaluate("#daysRequested <= 2", Map.of("daysRequested", BigDecimal.TEN));
        assertThat(result).isFalse();
    }

    @Test
    void missingVariableFailsClosedRatherThanThrowing() {
        boolean result = evaluator.evaluate("#daysRequested <= 2", Map.of());
        assertThat(result).isFalse();
    }

    @Test
    void malformedExpressionFailsClosedRatherThanThrowing() {
        boolean result = evaluator.evaluate("#daysRequested <=", Map.of("daysRequested", 1));
        assertThat(result).isFalse();
    }

    @Test
    void nonBooleanResultFailsClosed() {
        boolean result = evaluator.evaluate("#daysRequested", Map.of("daysRequested", 1));
        assertThat(result).isFalse();
    }
}
