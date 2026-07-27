package com.ewos.workflow.application;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Evaluates a {@code WorkflowTransition.guardExpression} against the variables a {@link
 * WorkflowVariableResolver} exposes for the instance's subject — the auto-approval / auto-rejection
 * rule mechanism (Sprint 4). The {@code guard_expression} column has existed since V11 but was
 * never evaluated (see the module's "Deferred" notes); a blank/null guard still always passes,
 * preserving every pre-Sprint-4 definition's behaviour exactly.
 *
 * <p>Expressions reference resolved variables with SpEL's {@code #name} syntax, e.g. {@code
 * #daysRequested <= 2}. Evaluation uses {@link SimpleEvaluationContext#forReadOnlyDataBinding()} —
 * no bean references, no constructors, no static/method invocation on arbitrary types — so a
 * tenant-authored expression cannot reach outside the variable map it was given.
 */
@Component
public class WorkflowGuardEvaluator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGuardEvaluator.class);
    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("#(\\w+)");

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * True when the guard is blank/null, or evaluates to boolean true. False on any error — and
     * false, not "unresolved-comparison-happens-to-be-true", when the expression references a
     * variable that isn't in {@code variables}: SpEL resolves an unset {@code #name} to {@code
     * null}, and its relational operators treat null as sorting below every value, so {@code
     * #missing <= 2} would otherwise evaluate to {@code true} — silently auto-approving on missing
     * data instead of failing closed. Checked with a plain token scan rather than reflection, since
     * guard expressions are simple comparisons, not general SpEL programs.
     */
    public boolean evaluate(String guardExpression, Map<String, Object> variables) {
        if (guardExpression == null || guardExpression.isBlank()) {
            return true;
        }
        Matcher referenced = VARIABLE_REFERENCE.matcher(guardExpression);
        while (referenced.find()) {
            if (!variables.containsKey(referenced.group(1))) {
                log.warn(
                        "Guard expression '{}' references unresolved variable '{}' — treating as"
                                + " false",
                        guardExpression,
                        referenced.group(1));
                return false;
            }
        }
        try {
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            variables.forEach(context::setVariable);
            Expression expression = parser.parseExpression(guardExpression);
            Boolean result = expression.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException e) {
            log.warn("Guard expression '{}' failed to evaluate — treating as false", guardExpression, e);
            return false;
        }
    }
}
