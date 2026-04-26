package io.github.luanolegario.eventguard.api;

import java.lang.reflect.Method;

public interface KeyNamespaceStrategy {

    /**
     * Composes the persisted key namespace to prevent collisions across listeners.
     */
    String compose(String keyPrefix, Method method, String resolvedKey);
}

