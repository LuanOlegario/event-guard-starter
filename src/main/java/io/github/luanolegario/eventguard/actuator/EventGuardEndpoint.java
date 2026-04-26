package io.github.luanolegario.eventguard.actuator;

import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * Operations endpoint for manual Event Guard administration.
 */
@Endpoint(id = "eventguard")
public class EventGuardEndpoint {

    private final IdempotencyLockProvider idempotencyLockProvider;

    public EventGuardEndpoint(IdempotencyLockProvider idempotencyLockProvider) {
        Assert.notNull(idempotencyLockProvider, "idempotencyLockProvider must not be null");
        this.idempotencyLockProvider = idempotencyLockProvider;
    }

    @DeleteOperation
    public Map<String, Object> releaseLock(String lockKey, String token) {
        Assert.hasText(lockKey, "lockKey must not be blank");
        Assert.hasText(token, "token must not be blank");

        idempotencyLockProvider.release(lockKey, token);
        return Map.of(
            "released", true,
            "lockKey", lockKey
        );
    }
}
