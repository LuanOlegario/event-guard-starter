package io.github.luanolegario.eventguard;

import io.github.luanolegario.eventguard.annotation.EventGuardListener;
import io.github.luanolegario.eventguard.aop.EventGuardAspect;
import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import io.github.luanolegario.eventguard.model.LockAcquisition;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = EventGuardIntegrationTest.TestApplication.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
    }
)
@Testcontainers
class EventGuardIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
        .withExposedPorts(6379);

    @Autowired
    private DummyListener dummyListener;

    @Autowired(required = false)
    private IdempotencyLockProvider idempotencyLockProvider;

    @Autowired(required = false)
    private EventGuardAspect eventGuardAspect;

    @Test
    void shouldProcessIdenticalMessagesOnlyOnce() {
        assertThat(idempotencyLockProvider).isNotNull();
        assertThat(eventGuardAspect).isNotNull();

        TestEvent duplicatedEvent = new TestEvent("evt-123", "payload");

        send(duplicatedEvent);
        send(duplicatedEvent);

        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(dummyListener.invocationCount()).isEqualTo(1));

        Awaitility.await()
            .during(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(dummyListener.invocationCount()).isEqualTo(1));
    }

    private void send(TestEvent event) {
        Message<TestEvent> message = MessageBuilder.withPayload(event).build();
        dummyListener.onMessage(message);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(TestBeans.class)
    static class TestApplication {
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        DummyListener dummyListener() {
            return new DummyListener();
        }

        @Bean
        IdempotencyLockProvider idempotencyLockProvider() {
            return new InMemoryIdempotencyLockProvider();
        }
    }

    static class InMemoryIdempotencyLockProvider implements IdempotencyLockProvider {

        private final ConcurrentMap<String, String> locks = new ConcurrentHashMap<>();

        @Override
        public LockAcquisition tryAcquire(String key, Duration ttl) {
            String token = "token-" + key;
            boolean acquired = locks.putIfAbsent(key, token) == null;
            return new LockAcquisition(acquired, acquired ? Instant.now().plus(ttl) : null, acquired ? token : null);
        }

        @Override
        public void release(String key, String token) {
            locks.remove(key, token);
        }
    }

    static class DummyListener {

        private final AtomicInteger invocationCounter = new AtomicInteger();

        @EventGuardListener(idempotencyKey = "#event.id", releaseOnFailure = false)
        public void onMessage(Message<TestEvent> message) {
            invocationCounter.incrementAndGet();
        }

        int invocationCount() {
            return invocationCounter.get();
        }
    }

    record TestEvent(String id, String payload) {
    }
}
