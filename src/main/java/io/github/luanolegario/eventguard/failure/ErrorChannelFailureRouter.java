package io.github.luanolegario.eventguard.failure;

import io.github.luanolegario.eventguard.api.FailureRouter;
import io.github.luanolegario.eventguard.model.FailureEnvelope;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Routes failures to a Spring Messaging channel, defaulting to {@code errorChannel}.
 */
public final class ErrorChannelFailureRouter implements FailureRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorChannelFailureRouter.class);
    private static final String DEFAULT_ERROR_CHANNEL = "errorChannel";
    private static final Set<String> TRACE_HEADER_NAMES = Set.of(
        "traceparent",
        "b3",
        "X-B3-TraceId",
        "X-B3-SpanId",
        "X-B3-Sampled"
    );

    private final ListableBeanFactory beanFactory;
    private final ObjectProvider<?> tracerProvider;

    public ErrorChannelFailureRouter(ListableBeanFactory beanFactory) {
        this(beanFactory, null);
    }

    public ErrorChannelFailureRouter(ListableBeanFactory beanFactory, ObjectProvider<?> tracerProvider) {
        Assert.notNull(beanFactory, "beanFactory must not be null");
        this.beanFactory = beanFactory;
        this.tracerProvider = tracerProvider;
    }

    @Override
    public void route(FailureEnvelope failure) {
        Assert.notNull(failure, "failure must not be null");

        MessageChannel messageChannel = resolveChannel(failure.configuredFailureChannel());
        if (messageChannel == null) {
            LOGGER.debug("No failure channel resolved. Skipping routing for method {}", failure.listenerMethod());
            return;
        }

        Map<String, Object> headers = buildHeaders(failure);
        enrichTracingHeaders(headers, failure);
        Message<Throwable> errorMessage = MessageBuilder.withPayload(failure.cause())
            .copyHeaders(headers)
            .build();
        boolean accepted = messageChannel.send(errorMessage);
        if (!accepted) {
            throw new IllegalStateException("Failure message was rejected by channel: " + messageChannel);
        }
    }

    private MessageChannel resolveChannel(String configuredChannel) {
        if (StringUtils.hasText(configuredChannel) && beanFactory.containsBean(configuredChannel)) {
            return beanFactory.getBean(configuredChannel, MessageChannel.class);
        }
        if (beanFactory.containsBean(DEFAULT_ERROR_CHANNEL)) {
            return beanFactory.getBean(DEFAULT_ERROR_CHANNEL, MessageChannel.class);
        }
        return null;
    }

    private Map<String, Object> buildHeaders(FailureEnvelope failure) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("eventGuard.listenerMethod", failure.listenerMethod().toGenericString());
        headers.put("eventGuard.failureChannel", failure.configuredFailureChannel());
        if (failure.originalMessage() != null) {
            headers.put("eventGuard.originalPayload", failure.originalMessage().getPayload());
            headers.put("eventGuard.originalHeaders", failure.originalMessage().getHeaders());
        }
        return headers;
    }

    private void enrichTracingHeaders(Map<String, Object> headers, FailureEnvelope failure) {
        copyInboundTraceHeaders(headers, failure);
        if (tracerProvider == null) {
            return;
        }

        Object tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return;
        }

        try {
            Object currentSpan = invokeNoArg(tracer, "currentSpan");
            if (currentSpan == null) {
                return;
            }

            Object traceContext = invokeNoArg(currentSpan, "context");
            if (traceContext == null) {
                return;
            }

            String traceId = toText(invokeNoArg(traceContext, "traceId"));
            String spanId = toText(invokeNoArg(traceContext, "spanId"));
            if (!StringUtils.hasText(traceId) || !StringUtils.hasText(spanId)) {
                return;
            }

            headers.putIfAbsent("X-B3-TraceId", traceId);
            headers.putIfAbsent("X-B3-SpanId", spanId);
            headers.putIfAbsent("b3", traceId + "-" + spanId + "-1");

            String traceparent = toTraceparent(traceId, spanId);
            if (traceparent != null) {
                headers.putIfAbsent("traceparent", traceparent);
            }
        } catch (ReflectiveOperationException ex) {
            LOGGER.debug("Failed to enrich failure message with tracing headers", ex);
        }
    }

    private void copyInboundTraceHeaders(Map<String, Object> headers, FailureEnvelope failure) {
        Message<?> originalMessage = failure.originalMessage();
        if (originalMessage == null) {
            return;
        }
        for (String headerName : TRACE_HEADER_NAMES) {
            Object value = originalMessage.getHeaders().get(headerName);
            if (value != null) {
                headers.putIfAbsent(headerName, value);
            }
        }
    }

    private Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String toTraceparent(String traceId, String spanId) {
        String normalizedTraceId = normalizeTraceId(traceId);
        String normalizedSpanId = normalizeSpanId(spanId);
        if (normalizedTraceId == null || normalizedSpanId == null) {
            return null;
        }
        return "00-" + normalizedTraceId + "-" + normalizedSpanId + "-01";
    }

    private String normalizeTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return null;
        }
        String value = traceId.trim().toLowerCase();
        if (value.length() == 16) {
            value = "0000000000000000" + value;
        }
        return value.length() == 32 ? value : null;
    }

    private String normalizeSpanId(String spanId) {
        if (!StringUtils.hasText(spanId)) {
            return null;
        }
        String value = spanId.trim().toLowerCase();
        return value.length() == 16 ? value : null;
    }
}
