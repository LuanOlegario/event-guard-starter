package io.github.luanolegario.eventguard.api;

import io.github.luanolegario.eventguard.model.GuardMessageContext;

public interface SpelKeyEvaluator {

    /**
     * Evaluates the expression against the listener invocation context and returns a key.
     */
    String evaluate(String expression, GuardMessageContext context);
}

