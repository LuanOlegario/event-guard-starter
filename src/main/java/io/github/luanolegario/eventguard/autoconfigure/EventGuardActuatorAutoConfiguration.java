package io.github.luanolegario.eventguard.autoconfigure;

import io.github.luanolegario.eventguard.actuator.EventGuardEndpoint;
import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Endpoint.class)
public class EventGuardActuatorAutoConfiguration {

    @Bean
    @ConditionalOnBean(IdempotencyLockProvider.class)
    @ConditionalOnAvailableEndpoint(endpoint = EventGuardEndpoint.class)
    @ConditionalOnMissingBean(EventGuardEndpoint.class)
    public EventGuardEndpoint eventGuardEndpoint(IdempotencyLockProvider idempotencyLockProvider) {
        return new EventGuardEndpoint(idempotencyLockProvider);
    }
}

