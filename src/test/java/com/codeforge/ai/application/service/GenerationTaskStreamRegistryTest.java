package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import com.codeforge.ai.domain.task.enums.GenerationStreamStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationTaskStreamRegistryTest {

    private GenerationTaskStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GenerationTaskStreamRegistry(new PublicGenerationStreamEventMapper(new ObjectMapper()));
    }

    @Test
    void shouldRegisterSubscriberForNonTerminalTask() {
        SseEmitter emitter = registry.subscribe(
                1001L,
                null,
                () -> List.of(),
                ignored -> List.of(),
                () -> false);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(1001L)).isEqualTo(1);
    }

    @Test
    void shouldRemoveSubscriberWhenTerminalEventPublished() {
        registry.subscribe(1002L, null, () -> List.of(), ignored -> List.of(), () -> false);

        registry.publish(1002L, event("1", "1002", "TASK_CANCELLED", true), true);

        assertThat(registry.subscriberCount(1002L)).isEqualTo(0);
    }

    @Test
    void shouldNotRegisterSubscriberForTerminalTaskHistoryReplay() {
        SseEmitter emitter = registry.subscribe(
                1003L,
                null,
                () -> List.of(event("2", "1003", "TASK_SUCCESS", true)),
                ignored -> List.of(),
                () -> true);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(1003L)).isEqualTo(0);
    }

    private PublicGenerationStreamEvent event(String eventId, String taskId, String type, boolean terminal) {
        return new PublicGenerationStreamEvent(
                eventId,
                taskId,
                type,
                GenerationStreamStage.TERMINAL.name(),
                type,
                LocalDateTime.of(2026, 6, 22, 20, 30),
                terminal,
                java.util.Map.of()
        );
    }
}
