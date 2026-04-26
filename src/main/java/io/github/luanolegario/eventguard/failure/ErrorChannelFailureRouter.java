package io.github.luanolegario.eventguard.failure;

import io.github.luanolegario.eventguard.api.FailureRouter;
import io.github.luanolegario.eventguard.model.FailureEnvelope;
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

/**
 * Routes failures to a Spring Messaging channel, defaulting to {@code errorChannel}.
 */
public final class ErrorChannelFailureRouter implements FailureRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorChannelFailureRouter.class);
    private static final String DEFAULT_ERROR_CHANNEL = "errorChannel";

    private final ListableBeanFactory beanFactory;

    public ErrorChannelFailureRouter(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void route(FailureEnvelope failure) {
        Assert.notNull(failure, "failure must not be null");

        MessageChannel messageChannel = resolveChannel(failure.configuredFailureChannel());
        if (messageChannel == null) {
            LOGGER.debug("No failure channel resolved. Skipping routing for method {}", failure.listenerMethod());
            return;
        }

        Message<Throwable> errorMessage = MessageBuilder.withPayload(failure.cause())
            .copyHeaders(buildHeaders(failure))
            .build();
        messageChannel.send(errorMessage);
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
}

