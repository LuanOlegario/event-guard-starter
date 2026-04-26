package io.github.luanolegario.eventguard.idempotency;

import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import io.github.luanolegario.eventguard.model.LockAcquisition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * JDBC-backed idempotency lock provider.
 *
 * <p>Expected table:
 * <pre>
 * event_guard_locks (
 *   lock_key VARCHAR PRIMARY KEY,
 *   token VARCHAR,
 *   expires_at TIMESTAMP
 * )
 * </pre>
 */
public final class JdbcIdempotencyLockProvider implements IdempotencyLockProvider {

    private static final String INSERT_SQL =
        "INSERT INTO event_guard_locks (lock_key, token, expires_at) VALUES (?, ?, ?)";
    private static final String RELEASE_SQL =
        "DELETE FROM event_guard_locks WHERE lock_key = ? AND token = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyLockProvider(JdbcTemplate jdbcTemplate) {
        Assert.notNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LockAcquisition tryAcquire(String key, Duration ttl) {
        Assert.hasText(key, "key must not be blank");
        Assert.notNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be greater than zero");
        }

        Instant expiresAt = Instant.now().plus(ttl);
        String token = UUID.randomUUID().toString();
        try {
            int updated = jdbcTemplate.update(INSERT_SQL, key, token, Timestamp.from(expiresAt));
            if (updated == 1) {
                return new LockAcquisition(true, expiresAt, token);
            }
            return new LockAcquisition(false, null, null);
        } catch (DuplicateKeyException ex) {
            return new LockAcquisition(false, null, null);
        }
    }

    @Override
    public void release(String key, String token) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(token)) {
            return;
        }
        jdbcTemplate.update(RELEASE_SQL, key, token);
    }
}
