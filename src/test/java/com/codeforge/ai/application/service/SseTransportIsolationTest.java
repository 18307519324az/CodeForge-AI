package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import com.codeforge.ai.domain.task.enums.GenerationStreamStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseTransportIsolationTest {

    private PublicGenerationStreamEventMapper mapper;
    private GenerationTaskStreamRegistry registry;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        mapper = new PublicGenerationStreamEventMapper(new ObjectMapper());
        registry = new GenerationTaskStreamRegistry(mapper);
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void sseSubscriberSendFailureDoesNotFailGeneration() {
        FailingSseEmitter failingEmitter = new FailingSseEmitter(new IOException("client disconnected"));
        registry.subscribeWithEmitter(7001L, failingEmitter, null, () -> List.of(), ignored -> List.of(), () -> false);

        assertThatCode(() -> registry.publish(7001L, event("1", "7001", "MODEL_DELTA"), false))
                .doesNotThrowAnyException();
        assertThat(registry.subscriberCount(7001L)).isZero();
    }

    @Test
    void sseAsyncContextErrorIsolation() {
        FailingSseEmitter failingEmitter = new FailingSseEmitter(
                new IllegalStateException("A non-container (application) thread attempted to use the AsyncContext"));
        registry.subscribeWithEmitter(7002L, failingEmitter, null, () -> List.of(), ignored -> List.of(), () -> false);

        assertThatCode(() -> registry.publish(7002L, event("2", "7002", "MODEL_CALL_FINISHED"), false))
                .doesNotThrowAnyException();
        assertThat(registry.subscriberCount(7002L)).isZero();
    }

    @Test
    void sseOnErrorStopsFutureSend() {
        ControllableSseEmitter emitter = new ControllableSseEmitter();
        registry.subscribeWithEmitter(7003L, emitter, null, () -> List.of(), ignored -> List.of(), () -> false);
        assertThat(registry.subscriberCount(7003L)).isEqualTo(1);

        emitter.failNextSend(true);
        registry.publish(7003L, event("3", "7003", "FILES_GENERATED"), false);
        assertThat(registry.subscriberCount(7003L)).isZero();

        int attemptsAfterDisconnect = emitter.sendAttempts().get();
        registry.publish(7003L, event("31", "7003", "VERSION_CREATED"), false);
        assertThat(emitter.sendAttempts().get()).isEqualTo(attemptsAfterDisconnect);
    }

    @Test
    void sseHeartbeatStopsAfterError() {
        FailingSseEmitter failingEmitter = new FailingSseEmitter(new IOException("disconnect"));
        registry.subscribeWithEmitter(7004L, failingEmitter, null, () -> List.of(), ignored -> List.of(), () -> false);

        registry.publish(7004L, event("4", "7004", "MODEL_DELTA"), false);
        assertThat(registry.subscriberCount(7004L)).isZero();

        assertThatCode(() -> registry.runHeartbeatSweepForTest()).doesNotThrowAnyException();
        assertThat(registry.subscriberCount(7004L)).isZero();
    }

    @Test
    void sseTerminalAndErrorRace() throws Exception {
        ControllableSseEmitter emitter = new ControllableSseEmitter();
        registry.subscribeWithEmitter(7005L, emitter, null, () -> List.of(), ignored -> List.of(), () -> false);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                barrier.await(2, TimeUnit.SECONDS);
                registry.publish(7005L, terminalEvent("5", "7005", "TASK_SUCCESS"), true);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                done.countDown();
            }
        });
        executor.submit(() -> {
            try {
                barrier.await(2, TimeUnit.SECONDS);
                emitter.triggerError(new IOException("race disconnect"));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                done.countDown();
            }
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        awaitUntil(() -> registry.subscriberCount(7005L) == 0);
    }

    @Test
    void sseCleanupIdempotent() throws Exception {
        ControllableSseEmitter emitter = new ControllableSseEmitter();
        registry.subscribeWithEmitter(7006L, emitter, null, () -> List.of(), ignored -> List.of(), () -> false);

        emitter.triggerError(new IOException("first"));
        emitter.triggerCompletion();
        registry.publish(7006L, terminalEvent("6", "7006", "TASK_SUCCESS"), true);
        registry.runHeartbeatSweepForTest();

        awaitUntil(() -> registry.subscriberCount(7006L) == 0);
    }

    @Test
    void oneBrokenSubscriberDoesNotBreakOthers() throws Exception {
        FailingSseEmitter broken = new FailingSseEmitter(new IOException("broken"));
        TrackingSseEmitter healthy = new TrackingSseEmitter();
        registry.subscribeWithEmitter(7007L, broken, null, () -> List.of(), ignored -> List.of(), () -> false);
        registry.subscribeWithEmitter(7007L, healthy, null, () -> List.of(), ignored -> List.of(), () -> false);
        assertThat(registry.subscriberCount(7007L)).isEqualTo(2);

        assertThatCode(() -> registry.publish(7007L, event("7", "7007", "MODEL_DELTA"), false))
                .doesNotThrowAnyException();

        awaitUntil(() -> registry.subscriberCount(7007L) == 1);
        assertThat(healthy.receivedCount()).isEqualTo(1);

        registry.publish(7007L, event("8", "7007", "MODEL_CALL_FINISHED"), false);
        awaitUntil(() -> healthy.receivedCount() == 2);
    }

    @Test
    void registrySingleHeartbeatSweep() {
        registry.subscribe(7008L, null, () -> List.of(), ignored -> List.of(), () -> false);
        registry.subscribe(7009L, null, () -> List.of(), ignored -> List.of(), () -> false);

        assertThat(registry.scheduledHeartbeatTaskCount()).isEqualTo(1);
    }

    @Test
    void generationTaskNotFailedWhenClientDisconnects() {
        RuntimeException asyncContext = new RuntimeException(
                "A non-container (application) thread attempted to use the AsyncContext after an error had occurred");
        assertThat(SseTransportFailures.isTransportFailure(asyncContext)).isTrue();
        assertThatCode(() -> registry.publish(7010L, event("9", "7010", "VERSION_CREATED"), false))
                .doesNotThrowAnyException();
    }

    @Test
    void transportExceptionNotExposedInTaskEvent() {
        PublicGenerationStreamEventMapper eventMapper = new PublicGenerationStreamEventMapper(new ObjectMapper());
        var entity = com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity.builder()
                .id(99L)
                .taskId(7011L)
                .eventType("TASK_FAILED")
                .eventMessage("A non-container (application) thread attempted to use the AsyncContext")
                .eventPayloadJson("{\"error\":\"AsyncListener.onError returned\"}")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 7, 11, 33, 20));

        PublicGenerationStreamEvent mapped = eventMapper.fromEntity(entity);
        assertThat(mapped.message()).doesNotContain("AsyncContext");
        assertThat(mapped.message()).doesNotContain("AsyncListener");
    }

    private void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Condition not met before timeout");
    }

    private PublicGenerationStreamEvent event(String eventId, String taskId, String type) {
        return new PublicGenerationStreamEvent(
                eventId,
                taskId,
                type,
                GenerationStreamStage.AI_MODEL.name(),
                type,
                LocalDateTime.of(2026, 7, 7, 11, 30),
                false,
                Map.of()
        );
    }

    private PublicGenerationStreamEvent terminalEvent(String eventId, String taskId, String type) {
        return new PublicGenerationStreamEvent(
                eventId,
                taskId,
                type,
                GenerationStreamStage.TERMINAL.name(),
                type,
                LocalDateTime.of(2026, 7, 7, 11, 33),
                true,
                Map.of()
        );
    }

    private static final class FailingSseEmitter extends SseEmitter {

        private final RuntimeException failure;

        private FailingSseEmitter(Throwable failure) {
            super(0L);
            this.failure = failure instanceof RuntimeException runtime
                    ? runtime
                    : new RuntimeException(failure);
        }

        @Override
        public void send(SseEventBuilder builder) {
            throw failure;
        }

        @Override
        public void send(Object object) {
            throw failure;
        }
    }

    private static final class ControllableSseEmitter extends SseEmitter {

        private final AtomicInteger sendAttempts = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean failNextSend = new AtomicBoolean(false);

        private ControllableSseEmitter() {
            super(60_000L);
        }

        AtomicInteger sendAttempts() {
            return sendAttempts;
        }

        void failNextSend(boolean fail) {
            failNextSend.set(fail);
        }

        void triggerError(Throwable error) {
            if (closed.compareAndSet(false, true)) {
                super.completeWithError(error);
            }
        }

        void triggerCompletion() {
            if (closed.compareAndSet(false, true)) {
                super.complete();
            }
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (closed.get()) {
                throw new IOException("already closed");
            }
            if (failNextSend.getAndSet(false)) {
                throw new IOException("broken pipe");
            }
            sendAttempts.incrementAndGet();
        }
    }

    private static final class TrackingSseEmitter extends SseEmitter {

        private final AtomicInteger receivedCount = new AtomicInteger();

        private TrackingSseEmitter() {
            super(60_000L);
        }

        int receivedCount() {
            return receivedCount.get();
        }

        @Override
        public void send(SseEventBuilder builder) {
            receivedCount.incrementAndGet();
        }
    }
}
