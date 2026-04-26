package io.github.luanolegario.eventguard.idempotency;

import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import io.github.luanolegario.eventguard.model.LockAcquisition;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed idempotency lock provider using atomic SETNX + TTL semantics.
 */
public final class RedisIdempotencyLockProvider implements IdempotencyLockProvider {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end",
        Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdempotencyLockProvider(StringRedisTemplate stringRedisTemplate) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate must not be null");
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public LockAcquisition tryAcquire(String key, Duration ttl) {
        Assert.hasText(key, "key must not be blank");
        Assert.notNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be greater than zero");
        }

        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, lockValue, ttl);
        boolean lockAcquired = Boolean.TRUE.equals(acquired);
        Instant expiresAt = lockAcquired ? Instant.now().plus(ttl) : null;
        String token = lockAcquired ? lockValue : null;
        return new LockAcquisition(lockAcquired, expiresAt, token);
    }

    @Override
    public void release(String key, String token) {
        if (StringUtils.hasText(key) && StringUtils.hasText(token)) {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(key), token);
        }
    }
}
