package io.github.luanolegario.eventguard.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables idempotent processing with failure routing for a message listener method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventGuardListener {

    /**
     * SpEL expression used to resolve the idempotency key.
     * Examples: {@code #event.transactionId}, {@code #headers['idempotencyKey']}.
     */
    String idempotencyKey();

    /**
     * Static prefix used when composing the final key stored in the lock provider.
     */
    String keyPrefix() default "event-guard";

    /**
     * Lock expiration in seconds.
     */
    long ttlSeconds() default 86_400;

    /**
     * Optional destination channel for exhausted failures. If empty, default error channel is used.
     */
    String failureChannel() default "";

    /**
     * If true, the lock is released when listener processing throws.
     */
    boolean releaseOnFailure() default true;
}

