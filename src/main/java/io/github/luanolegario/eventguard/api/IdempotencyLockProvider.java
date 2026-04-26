package io.github.luanolegario.eventguard.api;

import io.github.luanolegario.eventguard.model.LockAcquisition;

import java.time.Duration;

public interface IdempotencyLockProvider {

    /**
     * Tries to atomically acquire a lock for the provided key with an expiration time.
     */
    LockAcquisition tryAcquire(String key, Duration ttl);

    /**
     * Releases a previously acquired lock only when the ownership token matches.
     */
    void release(String key, String token);
}
