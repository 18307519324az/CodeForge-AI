package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalModelDeltaPreviewDoesNotFakeReceivedCharsTest {

    private PublicGenerationStreamEventMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PublicGenerationStreamEventMapper(new ObjectMapper());
    }

    @Test
    void legacyPreviewOnlyPayloadMustNotExposeReceivedChars() {
        String preview = "x".repeat(200);
        var entity = GenerationTaskEventEntity.builder()
                .id(101L)
                .taskId(55L)
                .eventType(GenerationTaskEventType.MODEL_DELTA.name())
                .eventMessage("Model response received")
                .eventPayloadJson("{\"preview\":\"" + preview + "\",\"modelName\":\"deepseek-chat\"}")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 6, 18, 0));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.type()).isEqualTo("MODEL_DELTA");
        assertThat(publicEvent.data()).doesNotContainKey("preview");
        assertThat(publicEvent.data()).doesNotContainKey("previewChars");
        assertThat(publicEvent.data()).doesNotContainKey("receivedChars");
        assertThat(publicEvent.data()).doesNotContainKey("modelName");
    }

    @Test
    void realReceivedCharsFromPayloadIsPreserved() {
        var entity = GenerationTaskEventEntity.builder()
                .id(102L)
                .taskId(55L)
                .eventType(GenerationTaskEventType.MODEL_DELTA.name())
                .eventMessage("Model response received")
                .eventPayloadJson("{\"receivedChars\":8192}")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 6, 18, 1));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.data()).containsEntry("receivedChars", 8192L);
        assertThat(publicEvent.data()).doesNotContainKey("preview");
    }
}
