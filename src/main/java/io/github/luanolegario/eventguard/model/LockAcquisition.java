package io.github.luanolegario.eventguard.model;

import java.time.Instant;

public record LockAcquisition(
    boolean acquired,
    Instant expiresAt,
    String token
) {
}
