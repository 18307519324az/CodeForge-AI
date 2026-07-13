package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import com.codeforge.ai.domain.task.enums.GenerationStreamStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationStreamTerminalContractTest {

    private GenerationTaskStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GenerationTaskStreamRegistry(new PublicGenerationStreamEventMapper(new ObjectMapper()));
    }

    @Test
    void runningTaskReceivesTerminalAndCleansUp() {
        registry.subscribe(8001L, null, () -> List.of(), ignored -> List.of(), () -> false);

        registry.publish(8001L, terminalEvent("20", "TASK_SUCCESS"), true);

        assertThat(registry.subscriberCount(8001L)).isZero();
    }

    @Test
    void completedTaskWithoutCursorReplaysTerminalAndCloses() {
        SseEmitter emitter = registry.subscribe(
                8002L,
                null,
                () -> List.of(terminalEvent("21", "TASK_SUCCESS")),
                ignored -> List.of(),
                () -> true);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(8002L)).isZero();
    }

    @Test
    void completedTaskWithTerminalCursorClosesImmediately() {
        SseEmitter emitter = registry.subscribe(
                8003L,
                21L,
                () -> List.of(),
                ignored -> List.of(terminalEvent("21", "TASK_SUCCESS")),
                () -> true);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(8003L)).isZero();
    }

    @Test
    void completedTaskWithCursorBeforeTerminalReplaysTerminalAndCloses() {
        SseEmitter emitter = registry.subscribe(
                8004L,
                20L,
                () -> List.of(),
                ignored -> List.of(terminalEvent("21", "TASK_SUCCESS")),
                () -> true);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(8004L)).isZero();
    }

    @Test
    void successTaskStatusFallbackClosesWhenTerminalEventMissing() {
        SseEmitter emitter = registry.subscribe(
                8005L,
                null,
                () -> List.of(event("30", "TASK_STARTED")),
                ignored -> List.of(),
                () -> true);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(8005L)).isZero();
    }

    private PublicGenerationStreamEvent terminalEvent(String eventId, String type) {
        return new PublicGenerationStreamEvent(
                eventId,
                "8000",
                type,
                GenerationStreamStage.TERMINAL.name(),
                type,
                LocalDateTime.of(2026, 7, 6, 18, 0),
                true,
                Map.of()
        );
    }

    private PublicGenerationStreamEvent event(String eventId, String type) {
        return new PublicGenerationStreamEvent(
                eventId,
                "8000",
                type,
                GenerationStreamStage.TASK.name(),
                type,
                LocalDateTime.of(2026, 7, 6, 18, 0),
                false,
                Map.of()
        );
    }
}
