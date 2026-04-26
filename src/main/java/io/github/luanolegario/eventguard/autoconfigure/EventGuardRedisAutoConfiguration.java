package io.github.luanolegario.eventguard.autoconfigure;

import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import io.github.luanolegario.eventguard.idempotency.RedisIdempotencyLockProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
public class EventGuardRedisAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "eventguard.lock", name = "provider", havingValue = "redis", matchIfMissing = true)
    @ConditionalOnMissingBean(IdempotencyLockProvider.class)
    public IdempotencyLockProvider redisIdempotencyLockProvider(StringRedisTemplate stringRedisTemplate) {
        return new RedisIdempotencyLockProvider(stringRedisTemplate);
    }
}
