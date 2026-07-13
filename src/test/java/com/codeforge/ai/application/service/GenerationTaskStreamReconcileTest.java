package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import com.codeforge.ai.domain.task.enums.GenerationStreamStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationTaskStreamReconcileTest {

    private GenerationTaskStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GenerationTaskStreamRegistry(new PublicGenerationStreamEventMapper(new ObjectMapper()));
    }

    @Test
    void reconcileDeliversEventsCommittedDuringReplayWindow() {
        PublicGenerationStreamEvent event1 = event("1", "TASK_CREATED");
        PublicGenerationStreamEvent event2 = event("2", "TASK_STARTED");
        PublicGenerationStreamEvent event3 = event("3", "MODEL_CALL_STARTED");
        List<PublicGenerationStreamEvent> sentDuringReplay = List.of(event1, event2);
        List<PublicGenerationStreamEvent> missedAfterReplay = List.of(event3);
        AtomicReference<List<PublicGenerationStreamEvent>> reconcileResult = new AtomicReference<>();

        SseEmitter emitter = registry.subscribe(
                7001L,
                null,
                () -> sentDuringReplay,
                lastEventId -> {
                    if (lastEventId == 2L) {
                        reconcileResult.set(missedAfterReplay);
                        return missedAfterReplay;
                    }
                    return List.of();
                },
                () -> false);

        assertThat(emitter).isNotNull();
        assertThat(reconcileResult.get()).containsExactly(event3);
        assertThat(registry.subscriberCount(7001L)).isEqualTo(1);
    }

    @Test
    void publishBlocksDuringSubscribeReplayInsteadOfEarlyReturn() throws Exception {
        CountDownLatch replayStarted = new CountDownLatch(1);
        CountDownLatch allowReplayFinish = new CountDownLatch(1);
        List<PublicGenerationStreamEvent> history = List.of(event("1", "TASK_CREATED"));
        AtomicReference<Boolean> publishDelivered = new AtomicReference<>(false);

        Thread subscribeThread = new Thread(() -> registry.subscribe(
                7002L,
                null,
                () -> {
                    replayStarted.countDown();
                    try {
                        allowReplayFinish.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return history;
                },
                ignored -> List.of(),
                () -> false), "subscribe-thread");
        subscribeThread.start();

        assertThat(replayStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread publishThread = new Thread(() -> {
            registry.publish(7002L, event("2", "TASK_STARTED"), false);
            publishDelivered.set(true);
        }, "publish-thread");
        publishThread.start();

        Thread.sleep(200);
        assertThat(publishDelivered.get()).isFalse();

        allowReplayFinish.countDown();
        publishThread.join(5000);
        subscribeThread.join(5000);

        assertThat(publishDelivered.get()).isTrue();
        assertThat(registry.subscriberCount(7002L)).isEqualTo(1);
    }

    private PublicGenerationStreamEvent event(String eventId, String type) {
        return new PublicGenerationStreamEvent(
                eventId,
                "7000",
                type,
                GenerationStreamStage.TASK.name(),
                type,
                LocalDateTime.of(2026, 7, 6, 18, 0),
                false,
                Map.of()
        );
    }
}
