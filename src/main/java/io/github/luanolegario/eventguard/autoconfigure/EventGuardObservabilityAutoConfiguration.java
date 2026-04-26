package io.github.luanolegario.eventguard.autoconfigure;

import io.github.luanolegario.eventguard.metrics.EventGuardMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class EventGuardObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(EventGuardMetrics.class)
    public EventGuardMetrics eventGuardMetrics(MeterRegistry meterRegistry) {
        return new EventGuardMetrics(meterRegistry);
    }
}

