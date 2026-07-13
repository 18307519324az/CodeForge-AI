package com.codeforge.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.*;

class StreamRegistryCleanupTest {

    private final GenerationTaskStreamRegistry registry =
            new GenerationTaskStreamRegistry(new PublicGenerationStreamEventMapper(new ObjectMapper()));

    @Test void shouldRemoveTaskLockWhenLastSubscriberLeaves() {
        registry.subscribe(42L, null, () -> java.util.List.of(), ignored -> java.util.List.of(), () -> false);
        assertThat(registry.subscriberCount(42L)).isEqualTo(1);
        registry.publish(42L, terminalEvent("99", "TASK_SUCCESS"), true);
        assertThat(registry.subscriberCount(42L)).isZero();
    }

    private com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent terminalEvent(String eventId, String type) {
        return new com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent(
                eventId,
                "42",
                type,
                com.codeforge.ai.domain.task.enums.GenerationStreamStage.TERMINAL.name(),
                type,
                java.time.LocalDateTime.of(2026, 7, 6, 18, 0),
                true,
                java.util.Map.of()
        );
    }

    @Test void shouldSubscribeAndComplete() throws Exception {
        SseEmitter emitter = registry.subscribe(1L, null, () -> java.util.List.of(), ignored -> java.util.List.of(), () -> false);
        assertThat(emitter).isNotNull();
        emitter.complete();
        Thread.sleep(100);
    }

    @Test void shouldSubscribeAndCompleteWithError() throws Exception {
        SseEmitter emitter = registry.subscribe(2L, null, () -> java.util.List.of(), ignored -> java.util.List.of(), () -> false);
        emitter.completeWithError(new RuntimeException("test"));
        Thread.sleep(100);
    }

    @Test void shouldSubscribeWithTimeOut() {
        SseEmitter emitter = registry.subscribe(3L, null, () -> java.util.List.of(), ignored -> java.util.List.of(), () -> false);
        assertThat(emitter).isNotNull();
        emitter.complete();
    }

    @Test void registryNonNullForValidTask() {
        SseEmitter e1 = registry.subscribe(5L, null, () -> java.util.List.of(), ignored -> java.util.List.of(), () -> false);
        assertThat(e1).isNotNull();
        e1.complete();
    }
}
