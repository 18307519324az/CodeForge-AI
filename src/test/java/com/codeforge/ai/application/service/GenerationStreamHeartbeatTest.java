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

class GenerationStreamHeartbeatTest {

    private PublicGenerationStreamEventMapper mapper;
    private GenerationTaskStreamRegistry registry;

    @BeforeEach
    void setUp() {
        mapper = new PublicGenerationStreamEventMapper(new ObjectMapper());
        registry = new GenerationTaskStreamRegistry(mapper);
    }

    @Test
    void heartbeatEventHasNoPersistedEventId() {
        PublicGenerationStreamEvent heartbeat = mapper.heartbeat(9001L);

        assertThat(heartbeat.type()).isEqualTo("HEARTBEAT");
        assertThat(heartbeat.eventId()).isNull();
        assertThat(heartbeat.data()).isEmpty();
    }

    @Test
    void terminalSubscriberDoesNotRemainRegistered() {
        SseEmitter emitter = registry.subscribe(
                9002L,
                null,
                () -> List.of(terminalEvent("10", "9002", "TASK_SUCCESS")),
                ignored -> List.of(),
                () -> true);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(9002L)).isZero();
    }

    @Test
    void publishTerminalRemovesSubscriber() {
        registry.subscribe(9003L, null, () -> List.of(), ignored -> List.of(), () -> false);

        registry.publish(9003L, terminalEvent("11", "9003", "TASK_SUCCESS"), true);

        assertThat(registry.subscriberCount(9003L)).isZero();
    }

    private PublicGenerationStreamEvent terminalEvent(String eventId, String taskId, String type) {
        return new PublicGenerationStreamEvent(
                eventId,
                taskId,
                type,
                GenerationStreamStage.TERMINAL.name(),
                type,
                LocalDateTime.of(2026, 7, 6, 18, 0),
                true,
                Map.of()
        );
    }
}
