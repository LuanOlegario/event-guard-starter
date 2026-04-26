package io.github.luanolegario.eventguard.api;

import io.github.luanolegario.eventguard.model.FailureEnvelope;

public interface FailureRouter {

    /**
     * Routes a failure after listener execution error.
     */
    void route(FailureEnvelope failure);
}

