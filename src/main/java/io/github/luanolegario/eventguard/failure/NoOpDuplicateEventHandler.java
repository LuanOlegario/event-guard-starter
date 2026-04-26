package io.github.luanolegario.eventguard.failure;

import io.github.luanolegario.eventguard.api.DuplicateEventHandler;
import io.github.luanolegario.eventguard.model.DuplicateEventContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default duplicate strategy that skips processing and only logs the occurrence.
 */
public final class NoOpDuplicateEventHandler implements DuplicateEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpDuplicateEventHandler.class);

    @Override
    public void onDuplicate(DuplicateEventContext context) {
        LOGGER.debug("Duplicate event ignored for key={} method={}",
            context.idempotencyKey(),
            context.listenerMethod().toGenericString());
    }
}

