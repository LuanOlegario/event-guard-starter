package io.github.luanolegario.eventguard.aop;

import io.github.luanolegario.eventguard.annotation.EventGuardListener;
import io.github.luanolegario.eventguard.api.DuplicateEventHandler;
import io.github.luanolegario.eventguard.api.FailureRouter;
import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import io.github.luanolegario.eventguard.api.KeyNamespaceStrategy;
import io.github.luanolegario.eventguard.api.SpelKeyEvaluator;
import io.github.luanolegario.eventguard.metrics.EventGuardMetrics;
import io.github.luanolegario.eventguard.model.DuplicateEventContext;
import io.github.luanolegario.eventguard.model.FailureEnvelope;
import io.github.luanolegario.eventguard.model.GuardMessageContext;
import io.github.luanolegario.eventguard.model.LockAcquisition;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;

/**
 * Cross-cutting idempotency guard for methods annotated with {@link EventGuardListener}.
 */
@Aspect
public final class EventGuardAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventGuardAspect.class);

    private final IdempotencyLockProvider idempotencyLockProvider;
    private final SpelKeyEvaluator spelKeyEvaluator;
    private final FailureRouter failureRouter;
    private final DuplicateEventHandler duplicateEventHandler;
    private final KeyNamespaceStrategy keyNamespaceStrategy;
    private final EventGuardMetrics eventGuardMetrics;

    public EventGuardAspect(
        IdempotencyLockProvider idempotencyLockProvider,
        SpelKeyEvaluator spelKeyEvaluator,
        FailureRouter failureRouter,
        DuplicateEventHandler duplicateEventHandler,
        KeyNamespaceStrategy keyNamespaceStrategy,
        EventGuardMetrics eventGuardMetrics
    ) {
        Assert.notNull(idempotencyLockProvider, "idempotencyLockProvider must not be null");
        Assert.notNull(spelKeyEvaluator, "spelKeyEvaluator must not be null");
        Assert.notNull(failureRouter, "failureRouter must not be null");
        Assert.notNull(duplicateEventHandler, "duplicateEventHandler must not be null");
        Assert.notNull(keyNamespaceStrategy, "keyNamespaceStrategy must not be null");
        Assert.notNull(eventGuardMetrics, "eventGuardMetrics must not be null");
        this.idempotencyLockProvider = idempotencyLockProvider;
        this.spelKeyEvaluator = spelKeyEvaluator;
        this.failureRouter = failureRouter;
        this.duplicateEventHandler = duplicateEventHandler;
        this.keyNamespaceStrategy = keyNamespaceStrategy;
        this.eventGuardMetrics = eventGuardMetrics;
    }

    @Around("@annotation(eventGuardListener)")
    public Object guard(ProceedingJoinPoint joinPoint, EventGuardListener eventGuardListener) throws Throwable {
        Method method = resolveMethod(joinPoint);
        GuardMessageContext context = buildContext(joinPoint, method);

        String resolvedKey = spelKeyEvaluator.evaluate(eventGuardListener.idempotencyKey(), context);
        String namespacedKey = keyNamespaceStrategy.compose(eventGuardListener.keyPrefix(), method, resolvedKey);
        Duration ttl = resolveTtl(eventGuardListener.ttlSeconds(), method);

        LockAcquisition acquisition = idempotencyLockProvider.tryAcquire(namespacedKey, ttl);
        if (!acquisition.acquired()) {
            eventGuardMetrics.incrementDuplicatesBlocked();
            duplicateEventHandler.onDuplicate(new DuplicateEventContext(resolveMessage(context), namespacedKey, method));
            return duplicateReturnValue(method.getReturnType());
        }

        eventGuardMetrics.incrementLocksAcquired();
        long startTimeNanos = System.nanoTime();
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            routeFailureSafely(context, method, eventGuardListener.failureChannel(), throwable);
            if (eventGuardListener.releaseOnFailure()) {
                releaseLockSafely(namespacedKey, acquisition.token(), throwable);
            }
            throw throwable;
        } finally {
            eventGuardMetrics.recordListenerExecution(Duration.ofNanos(System.nanoTime() - startTimeNanos));
        }
    }

    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method signatureMethod = methodSignature.getMethod();
        Object target = joinPoint.getTarget();
        Class<?> targetClass = target != null ? target.getClass() : signatureMethod.getDeclaringClass();
        return AopUtils.getMostSpecificMethod(signatureMethod, targetClass);
    }

    private GuardMessageContext buildContext(ProceedingJoinPoint joinPoint, Method method) {
        Object[] args = joinPoint.getArgs();
        Message<?> message = extractMessage(args);
        Object event = message != null ? message.getPayload() : extractPayloadArg(args);
        Map<String, Object> headers = message != null ? Map.copyOf(message.getHeaders()) : Map.of();
        Object target = joinPoint.getTarget();
        Class<?> targetClass = target != null ? target.getClass() : method.getDeclaringClass();
        return new GuardMessageContext(message, event, headers, args, method, targetClass);
    }

    private Message<?> extractMessage(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Message<?> message) {
                return message;
            }
        }
        return null;
    }

    private Object extractPayloadArg(Object[] args) {
        for (Object arg : args) {
            if (!(arg instanceof Message<?>)) {
                return arg;
            }
        }
        return null;
    }

    private Message<?> resolveMessage(GuardMessageContext context) {
        if (context.message() != null) {
            return context.message();
        }
        Object payload = context.event() != null ? context.event() : "";
        return MessageBuilder.withPayload(payload)
            .copyHeaders(context.headers())
            .build();
    }

    private Duration resolveTtl(long ttlSeconds, Method method) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("@EventGuardListener ttlSeconds must be > 0 on " + method.toGenericString());
        }
        return Duration.ofSeconds(ttlSeconds);
    }

    private void routeFailureSafely(
        GuardMessageContext context,
        Method method,
        String configuredFailureChannel,
        Throwable originalFailure
    ) {
        try {
            failureRouter.route(new FailureEnvelope(resolveMessage(context), originalFailure, configuredFailureChannel, method));
            eventGuardMetrics.incrementFailuresRouted();
        } catch (RuntimeException routingFailure) {
            originalFailure.addSuppressed(routingFailure);
            LOGGER.warn("Failure routing also failed for method {}", method.toGenericString(), routingFailure);
        }
    }

    private void releaseLockSafely(String lockKey, String token, Throwable originalFailure) {
        if (!StringUtils.hasText(lockKey) || !StringUtils.hasText(token)) {
            return;
        }
        try {
            idempotencyLockProvider.release(lockKey, token);
        } catch (RuntimeException releaseFailure) {
            originalFailure.addSuppressed(releaseFailure);
            LOGGER.warn("Lock release failed for key {}", lockKey, releaseFailure);
        }
    }

    private Object duplicateReturnValue(Class<?> returnType) {
        Assert.notNull(returnType, "returnType must not be null");
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        return switch (returnType.getName()) {
            case "boolean" -> false;
            case "char" -> '\0';
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            case "int" -> 0;
            case "long" -> 0L;
            case "float" -> 0.0f;
            case "double" -> 0.0d;
            default -> throw new IllegalStateException("Unsupported primitive return type: " + returnType.getName());
        };
    }
}
