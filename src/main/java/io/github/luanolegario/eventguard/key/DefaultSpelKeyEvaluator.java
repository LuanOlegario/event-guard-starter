package io.github.luanolegario.eventguard.key;

import io.github.luanolegario.eventguard.api.SpelKeyEvaluator;
import io.github.luanolegario.eventguard.model.GuardMessageContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default SpEL evaluator for idempotency keys.
 * <p>
 * Supported variables:
 * <ul>
 *     <li>{@code #event} / {@code #payload}</li>
 *     <li>{@code #headers}</li>
 *     <li>{@code #message}</li>
 *     <li>{@code #args}</li>
 *     <li>{@code #method}</li>
 *     <li>{@code #targetClass}</li>
 * </ul>
 */
public final class DefaultSpelKeyEvaluator implements SpelKeyEvaluator {

    private static final TypeLocator RESTRICTED_TYPE_LOCATOR = typeName -> {
        throw new EvaluationException("Type references are disabled in @EventGuardListener SpEL.");
    };

    private final ExpressionParser expressionParser;
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public DefaultSpelKeyEvaluator() {
        this.expressionParser = new SpelExpressionParser(new SpelParserConfiguration(false, false));
    }

    @Override
    public String evaluate(String expression, GuardMessageContext context) {
        Assert.hasText(expression, "expression must not be blank");
        Assert.notNull(context, "context must not be null");

        StandardEvaluationContext evaluationContext = new StandardEvaluationContext(context.event());
        evaluationContext.setTypeLocator(RESTRICTED_TYPE_LOCATOR);
        evaluationContext.setVariable("event", context.event());
        evaluationContext.setVariable("payload", context.event());
        evaluationContext.setVariable("headers", context.headers());
        evaluationContext.setVariable("message", context.message());
        evaluationContext.setVariable("args", context.args());
        evaluationContext.setVariable("method", context.method());
        evaluationContext.setVariable("targetClass", context.targetClass());

        Object evaluatedValue;
        try {
            evaluatedValue = parseExpression(expression).getValue(evaluationContext);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Failed to evaluate idempotency key expression: " + expression, ex);
        }

        if (evaluatedValue == null) {
            throw new IllegalArgumentException("Idempotency key expression resolved to null: " + expression);
        }

        String resolvedKey = String.valueOf(evaluatedValue).trim();
        if (!StringUtils.hasText(resolvedKey)) {
            throw new IllegalArgumentException("Idempotency key expression resolved to blank value: " + expression);
        }
        return resolvedKey;
    }

    private Expression parseExpression(String expression) {
        return expressionCache.computeIfAbsent(expression, expressionParser::parseExpression);
    }
}

