package io.github.luanolegario.eventguard.model;

import org.springframework.messaging.Message;

import java.lang.reflect.Method;

public record DuplicateEventContext(
    Message<?> message,
    String idempotencyKey,
    Method listenerMethod
) {
}

