package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiDirectStreamingNoRawDeltaExposureTest {

    private PublicGenerationStreamEventMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PublicGenerationStreamEventMapper(new ObjectMapper());
    }

    @Test
    void modelDeltaMustNotExposeRawGenerationPayload() {
        String dangerousPayload = """
                {"attempt":1,"receivedChars":4812,"chunkCount":37,"elapsedMs":21000,
                "content":"<!DOCTYPE html>","delta":"secret","files":[{"path":"index.html"}],
                "systemPrompt":"hidden","developerPrompt":"hidden","preview":"raw"}
                """;

        var entity = GenerationTaskEventEntity.builder()
                .id(200L)
                .taskId(83L)
                .eventType(GenerationTaskEventType.MODEL_DELTA.name())
                .eventMessage("AI 正在生成项目内容")
                .eventPayloadJson(dangerousPayload)
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 7, 19, 0));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.data()).containsEntry("attempt", 1L);
        assertThat(publicEvent.data()).containsEntry("receivedChars", 4812L);
        assertThat(publicEvent.data()).containsEntry("chunkCount", 37L);
        assertThat(publicEvent.data()).containsEntry("elapsedMs", 21000L);
        assertThat(publicEvent.data()).doesNotContainKeys(
                "content", "delta", "preview", "files", "systemPrompt", "developerPrompt", "response", "raw");
        assertThat(publicEvent.message()).doesNotContain("<html");
    }
}
