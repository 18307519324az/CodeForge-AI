package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AsyncGenerationTaskExecutionDispatcherTest {

    private GenerationTaskEntityMapper generationTaskEntityMapper;
    private AiAppEntityMapper aiAppEntityMapper;
    private AiDirectGenerationApplicationService aiDirectGenerationApplicationService;
    private AsyncGenerationTaskExecutionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        aiDirectGenerationApplicationService = mock(AiDirectGenerationApplicationService.class);
        dispatcher = new AsyncGenerationTaskExecutionDispatcher(
                generationTaskEntityMapper,
                aiAppEntityMapper,
                aiDirectGenerationApplicationService,
                new ObjectMapper(),
                Runnable::run);
    }

    @Test
    void shouldDelegateExecutionToAiDirectServiceWithPinnedTemplateColumns() {
        GenerationTaskEntity taskEntity = GenerationTaskEntity.builder()
                .id(88L)
                .appId(3001L)
                .taskStatus(GenerationTaskStatus.QUEUED.name())
                .promptTemplateId(4001L)
                .promptTemplateVersionId(5001L)
                .requestPayloadJson("""
                        {"requirement":"build landing page","promptTemplateId":4001,"promptTemplateVersionId":5001}
                        """)
                .build();
        AiAppEntity appEntity = AiAppEntity.builder()
                .id(3001L)
                .name("Demo App")
                .appType("WEB_APP")
                .build();
        given(generationTaskEntityMapper.selectOneById(88L)).willReturn(taskEntity);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(appEntity);

        dispatcher.executeTaskLifecycle(88L, 2001L, "req-async-1");

        verify(aiDirectGenerationApplicationService).executeSync(
                eq(taskEntity),
                eq(appEntity),
                eq("build landing page"),
                eq(2001L),
                eq("req-async-1"));
    }
}
