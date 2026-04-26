package io.github.luanolegario.eventguard.key;

import io.github.luanolegario.eventguard.api.KeyNamespaceStrategy;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

public final class DefaultKeyNamespaceStrategy implements KeyNamespaceStrategy {

    @Override
    public String compose(String keyPrefix, Method method, String resolvedKey) {
        Assert.notNull(method, "method must not be null");
        Assert.hasText(resolvedKey, "resolvedKey must not be blank");

        String safePrefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "event-guard";
        String methodNamespace = method.getDeclaringClass().getName() + "#" + method.getName();
        return safePrefix + ":" + methodNamespace + ":" + resolvedKey.trim();
    }
}

