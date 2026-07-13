package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.generation.model.ProviderErrorSanitizer;
import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicGenerationStreamEventMapperTest {

    private PublicGenerationStreamEventMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PublicGenerationStreamEventMapper(new ObjectMapper());
    }

    @Test
    void shouldMapModelDeltaWithoutPreviewLeak() {
        var entity = GenerationTaskEventEntity.builder()
                .id(88L)
                .taskId(12L)
                .eventType(GenerationTaskEventType.MODEL_DELTA.name())
                .eventMessage("Model response received")
                .eventPayloadJson("{\"preview\":\"secret code fragment\",\"modelName\":\"deepseek-chat\"}")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 6, 12, 30));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.eventId()).isEqualTo("88");
        assertThat(publicEvent.taskId()).isEqualTo("12");
        assertThat(publicEvent.type()).isEqualTo("MODEL_DELTA");
        assertThat(publicEvent.message()).isEqualTo("AI 正在生成项目内容");
        assertThat(publicEvent.data()).isEmpty();
        assertThat(publicEvent.data()).doesNotContainKey("preview");
        assertThat(publicEvent.data()).doesNotContainKey("receivedChars");
        assertThat(publicEvent.data()).doesNotContainKey("modelName");
    }

    @Test
    void shouldMapVersionCreatedWithSafeFieldsOnly() {
        var entity = GenerationTaskEventEntity.builder()
                .id(90L)
                .taskId(12L)
                .eventType(GenerationTaskEventType.VERSION_CREATED.name())
                .eventMessage("version created")
                .eventPayloadJson("{\"versionId\":9001,\"versionNo\":3,\"generationSource\":\"AI_DIRECT\"}")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 6, 12, 31));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.data()).containsEntry("versionId", "9001");
        assertThat(publicEvent.data()).containsEntry("versionNo", 3L);
        assertThat(publicEvent.data()).doesNotContainKey("generationSource");
    }

    @Test
    void shouldNormalizeDoubleVersionIdPayloadToIntegralString() {
        var entity = GenerationTaskEventEntity.builder()
                .id(92L)
                .taskId(12L)
                .eventType(GenerationTaskEventType.VERSION_CREATED.name())
                .eventMessage("version created")
                .eventPayloadJson("{\"versionId\":50.0,\"versionNo\":1}")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 7, 11, 33));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.data()).containsEntry("versionId", "50");
        assertThat(publicEvent.data()).containsEntry("versionNo", 1L);
    }

    @Test
    void publicVersionCreatedIdContractUsesStringDigitsOnly() {
        var entity = GenerationTaskEventEntity.builder()
                .id(93L)
                .taskId(83L)
                .eventType(GenerationTaskEventType.VERSION_CREATED.name())
                .eventMessage("version created")
                .eventPayloadJson("{\"versionId\":\"50.0\",\"versionNo\":1}")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 7, 11, 33));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.data()).doesNotContainKey("versionId");
        assertThat(publicEvent.data()).containsEntry("versionNo", 1L);
    }

    @Test
    void shouldMapTaskFailedWithSanitizedMessage() {
        var entity = GenerationTaskEventEntity.builder()
                .id(91L)
                .taskId(12L)
                .eventType(GenerationTaskEventType.TASK_FAILED.name())
                .eventMessage("生成失败：模型返回无效产物")
                .eventPayloadJson("{\"errorCode\":\"ARTIFACT_INVALID\",\"error\":\"模型返回无效产物\"}")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 6, 12, 32));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.terminal()).isTrue();
        assertThat(publicEvent.message()).isEqualTo("模型返回无效产物");
        assertThat(publicEvent.data()).containsEntry("errorCode", "ARTIFACT_INVALID");
        assertThat(publicEvent.data()).doesNotContainKey("error");
    }

    @Test
    void shouldSanitizeHistoricalProviderIoFailureMessage() {
        var entity = GenerationTaskEventEntity.builder()
                .id(94L)
                .taskId(136L)
                .eventType(GenerationTaskEventType.TASK_FAILED.name())
                .eventMessage("生成失败：所有 AI 模型供应商调用均失败，最后错误: 流式调用 I/O 错误: closed")
                .eventPayloadJson("""
                        {"errorCode":"GENERATION_ERROR","error":"所有 AI 模型供应商调用均失败，最后错误: 流式调用 I/O 错误: closed"}
                        """)
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 8, 20, 52, 59));

        var publicEvent = mapper.fromEntity(entity);

        assertThat(publicEvent.message()).isEqualTo(ProviderErrorSanitizer.PUBLIC_STREAM_INTERRUPTED);
        assertThat(publicEvent.message()).doesNotContain("closed");
        assertThat(publicEvent.data()).containsEntry("errorCode", "AI_STREAM_INTERRUPTED");
    }
}
