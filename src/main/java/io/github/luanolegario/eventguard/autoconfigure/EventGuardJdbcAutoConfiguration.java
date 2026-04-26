package io.github.luanolegario.eventguard.autoconfigure;

import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import io.github.luanolegario.eventguard.idempotency.JdbcIdempotencyLockProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
public class EventGuardJdbcAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "eventguard.lock", name = "provider", havingValue = "jdbc")
    @ConditionalOnMissingBean(IdempotencyLockProvider.class)
    public IdempotencyLockProvider jdbcIdempotencyLockProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyLockProvider(jdbcTemplate);
    }
}
