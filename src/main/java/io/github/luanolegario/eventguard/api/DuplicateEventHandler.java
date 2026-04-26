package io.github.luanolegario.eventguard.api;

import io.github.luanolegario.eventguard.model.DuplicateEventContext;

public interface DuplicateEventHandler {

    /**
     * Callback executed when an event is identified as duplicate.
     */
    void onDuplicate(DuplicateEventContext context);
}

