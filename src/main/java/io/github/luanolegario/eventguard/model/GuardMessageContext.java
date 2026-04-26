package io.github.luanolegario.eventguard.model;

import org.springframework.messaging.Message;

import java.lang.reflect.Method;
import java.util.Map;

public record GuardMessageContext(
    Message<?> message,
    Object event,
    Map<String, Object> headers,
    Object[] args,
    Method method,
    Class<?> targetClass
) {
}

