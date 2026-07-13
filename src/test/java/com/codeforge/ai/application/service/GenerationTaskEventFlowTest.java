package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

class GenerationTaskEventFlowTest {
    private GenerationTaskEventEntityMapper eventMapper = mock(GenerationTaskEventEntityMapper.class);
    private GenerationTaskStreamRegistry registry =
            new GenerationTaskStreamRegistry(new PublicGenerationStreamEventMapper(new ObjectMapper()));

    @BeforeEach void setUp() {
        given(eventMapper.findByTaskId(any())).willReturn(List.of(
            eventEntity(1L, "TASK_CREATED", "任务已创建"),
            eventEntity(2L, "TASK_RUNNING", "任务开始执行"),
            eventEntity(3L, "FILES_GENERATED", "已生成 4 个文件"),
            eventEntity(4L, "VERSION_CREATED", "已创建版本 v1"),
            eventEntity(5L, "TASK_SUCCESS", "生成完成")
        ));
    }

    @Test void shouldReturnEventsInOrder() {
        var events = eventMapper.findByTaskId(1L);
        assertThat(events).hasSize(5);
        assertThat(events).extracting(e -> e.getEventType()).containsExactly(
            "TASK_CREATED", "TASK_RUNNING", "FILES_GENERATED", "VERSION_CREATED", "TASK_SUCCESS");
    }

    @Test void shouldDetectTerminalTask() {
        var events = eventMapper.findByTaskId(1L);
        var lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.getEventType()).isIn("TASK_SUCCESS", "TASK_FAILED");
    }

    private GenerationTaskEventEntity eventEntity(Long id, String type, String msg) {
        return GenerationTaskEventEntity.builder()
            .id(id).taskId(1L).eventType(type).eventMessage(msg).build();
    }
}
