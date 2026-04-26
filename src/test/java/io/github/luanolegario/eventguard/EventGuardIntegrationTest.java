package io.github.luanolegario.eventguard;

import io.github.luanolegario.eventguard.annotation.EventGuardListener;
import io.github.luanolegario.eventguard.aop.EventGuardAspect;
import io.github.luanolegario.eventguard.api.IdempotencyLockProvider;
import io.github.luanolegario.eventguard.failure.ErrorChannelFailureRouter;
import io.github.luanolegario.eventguard.idempotency.RedisIdempotencyLockProvider;
import io.github.luanolegario.eventguard.model.FailureEnvelope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = EventGuardIntegrationTest.TestApplication.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
    }
)
@Testcontainers
class EventGuardIntegrationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
        .withExposedPorts(6379);

    @Autowired
    private DummyListener dummyListener;

    @Autowired
    private IdempotencyLockProvider idempotencyLockProvider;

    @Autowired
    private EventGuardAspect eventGuardAspect;

    @Autowired
    private QueueChannel errorChannel;

    @Test
    void shouldProcessIdenticalMessagesOnlyOnce() {
        assertThat(idempotencyLockProvider).isNotNull();
        assertThat(idempotencyLockProvider).isInstanceOf(RedisIdempotencyLockProvider.class);
        assertThat(eventGuardAspect).isNotNull();

        String eventId = "evt-" + UUID.randomUUID();
        TestEvent duplicatedEvent = new TestEvent(eventId, "payload");

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

    @Test
    void shouldRouteFailureWithTracingHeaders() {
        clearErrorChannel();
        TestEvent event = new TestEvent("evt-fail-" + UUID.randomUUID(), "payload");
        Message<TestEvent> message = MessageBuilder.withPayload(event).build();

        assertThatThrownBy(() -> dummyListener.onFailure(message))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("boom");

        Message<?> errorMessage = Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .until(() -> errorChannel.receive(100), received -> received != null);

        assertThat(errorMessage.getPayload()).isInstanceOf(IllegalStateException.class);
        assertThat(errorMessage.getHeaders().get("traceparent")).isEqualTo(TRACEPARENT);
        assertThat(errorMessage.getHeaders().get("X-B3-TraceId")).isEqualTo(TRACE_ID);
        assertThat(errorMessage.getHeaders().get("X-B3-SpanId")).isEqualTo(SPAN_ID);
        assertThat(errorMessage.getHeaders().get("b3")).isEqualTo(TRACE_ID + "-" + SPAN_ID + "-1");
    }

    @Test
    void shouldNotCrashWhenTracerProviderIsMissing() throws NoSuchMethodException {
        QueueChannel localErrorChannel = new QueueChannel();
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        when(beanFactory.containsBean("errorChannel")).thenReturn(true);
        when(beanFactory.getBean("errorChannel", MessageChannel.class)).thenReturn(localErrorChannel);

        ErrorChannelFailureRouter router = new ErrorChannelFailureRouter(beanFactory, null);
        FailureEnvelope failureEnvelope = new FailureEnvelope(
            MessageBuilder.withPayload(new TestEvent("evt-no-tracer", "payload")).build(),
            new IllegalStateException("boom"),
            "",
            DummyListener.class.getDeclaredMethod("onFailure", Message.class)
        );

        assertThatCode(() -> router.route(failureEnvelope)).doesNotThrowAnyException();
    }

    private void send(TestEvent event) {
        Message<TestEvent> message = MessageBuilder.withPayload(event).build();
        dummyListener.onMessage(message);
    }

    private void clearErrorChannel() {
        while (errorChannel.receive(0) != null) {
            // Drain leftovers from previous tests.
        }
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

        @Bean(name = "errorChannel")
        QueueChannel errorChannel() {
            return new QueueChannel();
        }

        @Bean
        Tracer tracer() {
            Tracer tracer = mock(Tracer.class);
            Span span = mock(Span.class);
            TraceContext traceContext = mock(TraceContext.class);
            when(tracer.currentSpan()).thenReturn(span);
            when(span.context()).thenReturn(traceContext);
            when(traceContext.traceId()).thenReturn(TRACE_ID);
            when(traceContext.spanId()).thenReturn(SPAN_ID);
            return tracer;
        }
    }

    static class DummyListener {

        private final AtomicInteger invocationCounter = new AtomicInteger();

        @EventGuardListener(idempotencyKey = "#event.id", releaseOnFailure = false)
        public void onMessage(Message<TestEvent> message) {
            invocationCounter.incrementAndGet();
        }

        @EventGuardListener(idempotencyKey = "#event.id", releaseOnFailure = false)
        public void onFailure(Message<TestEvent> message) {
            throw new IllegalStateException("boom-" + message.getPayload().id());
        }

        int invocationCount() {
            return invocationCounter.get();
        }
    }

    record TestEvent(String id, String payload) {
    }
}
