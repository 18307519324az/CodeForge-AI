package com.codeforge.ai.domain.prompt.model;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.task.entity.GenerationRecordEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PromptTemplateTraceResolverTest {

    @Mock
    private GenerationRecordEntityMapper generationRecordEntityMapper;
    @Mock
    private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    @Mock
    private PromptTemplateEntityMapper promptTemplateEntityMapper;

    private PromptTemplateTraceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PromptTemplateTraceResolver(
                generationRecordEntityMapper,
                generationTaskEntityMapper,
                promptTemplateVersionEntityMapper,
                promptTemplateEntityMapper
        );
    }

    @Test
    void shouldResolveTraceFromGenerationTaskColumnsFirst() {
        given(generationTaskEntityMapper.selectOneById(6001L)).willReturn(GenerationTaskEntity.builder()
                .id(6001L)
                .promptTemplateVersionId(5001L)
                .build());
        given(promptTemplateVersionEntityMapper.selectOneById(5001L)).willReturn(PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(3)
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .templateName("APP_PAGE_GEN")
                .build());

        PromptTemplateTrace trace = resolver.resolveByTaskId(6001L);

        assertThat(trace.promptTemplateVersionId()).isEqualTo(5001L);
        assertThat(trace.promptTemplateCode()).isEqualTo("APP_PAGE_GEN");
        assertThat(trace.promptTemplateVersionNo()).isEqualTo(3);
    }

    @Test
    void shouldResolveTraceFromTaskGenerationRecord() {
        given(generationTaskEntityMapper.selectOneById(6001L)).willReturn(null);
        given(generationRecordEntityMapper.findLatestByTaskId(6001L)).willReturn(GenerationRecordEntity.builder()
                .taskId(6001L)
                .promptTemplateVersionId(5001L)
                .build());
        given(promptTemplateVersionEntityMapper.selectOneById(5001L)).willReturn(PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(3)
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .templateName("APP_PAGE_GEN")
                .build());

        PromptTemplateTrace trace = resolver.resolveByTaskId(6001L);

        assertThat(trace.promptTemplateVersionId()).isEqualTo(5001L);
        assertThat(trace.promptTemplateCode()).isEqualTo("APP_PAGE_GEN");
        assertThat(trace.promptTemplateVersionNo()).isEqualTo(3);
    }
}
