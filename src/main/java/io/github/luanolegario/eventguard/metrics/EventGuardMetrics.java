package io.github.luanolegario.eventguard.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.util.Assert;

import java.time.Duration;

/**
 * Centralizes Event Guard metric registration and updates.
 */
public final class EventGuardMetrics {

    private static final EventGuardMetrics NO_OP = new EventGuardMetrics();

    private final Counter locksAcquiredCounter;
    private final Counter duplicatesBlockedCounter;
    private final Counter failuresRoutedCounter;
    private final Timer listenerExecutionTimer;
    private final boolean enabled;

    private EventGuardMetrics() {
        this.locksAcquiredCounter = null;
        this.duplicatesBlockedCounter = null;
        this.failuresRoutedCounter = null;
        this.listenerExecutionTimer = null;
        this.enabled = false;
    }

    public EventGuardMetrics(MeterRegistry meterRegistry) {
        Assert.notNull(meterRegistry, "meterRegistry must not be null");
        this.locksAcquiredCounter = Counter.builder("eventguard.locks.acquired")
            .description("Total number of acquired idempotency locks.")
            .register(meterRegistry);
        this.duplicatesBlockedCounter = Counter.builder("eventguard.duplicates.blocked")
            .description("Total number of duplicate events blocked by idempotency lock.")
            .register(meterRegistry);
        this.failuresRoutedCounter = Counter.builder("eventguard.failures.routed")
            .description("Total number of listener failures routed to a failure channel.")
            .register(meterRegistry);
        this.listenerExecutionTimer = Timer.builder("eventguard.listener.execution")
            .description("Execution time for guarded listener business logic.")
            .register(meterRegistry);
        this.enabled = true;
    }

    public static EventGuardMetrics noop() {
        return NO_OP;
    }

    public void incrementLocksAcquired() {
        if (enabled) {
            locksAcquiredCounter.increment();
        }
    }

    public void incrementDuplicatesBlocked() {
        if (enabled) {
            duplicatesBlockedCounter.increment();
        }
    }

    public void incrementFailuresRouted() {
        if (enabled) {
            failuresRoutedCounter.increment();
        }
    }

    public void recordListenerExecution(Duration duration) {
        if (enabled && duration != null && !duration.isNegative()) {
            listenerExecutionTimer.record(duration);
        }
    }
}

