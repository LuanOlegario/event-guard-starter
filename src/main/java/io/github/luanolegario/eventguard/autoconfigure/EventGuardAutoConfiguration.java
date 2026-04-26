package io.github.luanolegario.eventguard.autoconfigure;

import io.github.luanolegario.eventguard.aop.EventGuardAspect;
import io.github.luanolegario.eventguard.api.DuplicateEventHandler;
import io.github.luanolegario.eventguard.api.FailureRouter;
import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import io.github.luanolegario.eventguard.api.KeyNamespaceStrategy;
import io.github.luanolegario.eventguard.api.SpelKeyEvaluator;
import io.github.luanolegario.eventguard.failure.ErrorChannelFailureRouter;
import io.github.luanolegario.eventguard.failure.NoOpDuplicateEventHandler;
import io.github.luanolegario.eventguard.key.DefaultKeyNamespaceStrategy;
import io.github.luanolegario.eventguard.key.DefaultSpelKeyEvaluator;
import io.github.luanolegario.eventguard.metrics.EventGuardMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {
    EventGuardRedisAutoConfiguration.class,
    EventGuardJdbcAutoConfiguration.class,
    EventGuardObservabilityAutoConfiguration.class
})
public class EventGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpelKeyEvaluator.class)
    public SpelKeyEvaluator spelKeyEvaluator() {
        return new DefaultSpelKeyEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean(KeyNamespaceStrategy.class)
    public KeyNamespaceStrategy keyNamespaceStrategy() {
        return new DefaultKeyNamespaceStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(FailureRouter.class)
    public FailureRouter failureRouter(ListableBeanFactory beanFactory) {
        return new ErrorChannelFailureRouter(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean(DuplicateEventHandler.class)
    public DuplicateEventHandler duplicateEventHandler() {
        return new NoOpDuplicateEventHandler();
    }

    @Bean
    @ConditionalOnBean(IdempotencyLockProvider.class)
    @ConditionalOnMissingBean(EventGuardAspect.class)
    public EventGuardAspect eventGuardAspect(
        IdempotencyLockProvider idempotencyLockProvider,
        SpelKeyEvaluator spelKeyEvaluator,
        FailureRouter failureRouter,
        DuplicateEventHandler duplicateEventHandler,
        KeyNamespaceStrategy keyNamespaceStrategy,
        ObjectProvider<EventGuardMetrics> eventGuardMetrics
    ) {
        return new EventGuardAspect(
            idempotencyLockProvider,
            spelKeyEvaluator,
            failureRouter,
            duplicateEventHandler,
            keyNamespaceStrategy,
            eventGuardMetrics.getIfAvailable(EventGuardMetrics::noop)
        );
    }
}
