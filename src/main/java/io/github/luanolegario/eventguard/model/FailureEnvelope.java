package io.github.luanolegario.eventguard.model;

import org.springframework.messaging.Message;

import java.lang.reflect.Method;

public record FailureEnvelope(
    Message<?> originalMessage,
    Throwable cause,
    String configuredFailureChannel,
    Method listenerMethod
) {
}

