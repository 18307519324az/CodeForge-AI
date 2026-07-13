package com.codeforge.ai.domain.prompt.model;

import com.codeforge.ai.application.generation.AiCodegenPromptBuilder;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ModelCallLogPromptTraceTest {

    private PromptTemplateTraceResolver traceResolver;

    @BeforeEach
    void setUp() {
        GenerationRecordEntityMapper recordMapper = mock(GenerationRecordEntityMapper.class);
        GenerationTaskEntityMapper taskMapper = mock(GenerationTaskEntityMapper.class);
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        traceResolver = new PromptTemplateTraceResolver(recordMapper, taskMapper, versionMapper, templateMapper);

        given(taskMapper.selectOneById(6001L)).willReturn(GenerationTaskEntity.builder()
                .id(6001L)
                .promptTemplateVersionId(5001L)
                .build());
        given(versionMapper.selectOneById(5001L)).willReturn(com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .build());
        given(templateMapper.selectOneById(4001L)).willReturn(com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity.builder()
                .id(4001L)
                .templateName("trace-template")
                .build());
    }

    @Test
    void shouldPersistTemplateIdentityAndHashesFromOutgoingMessages() {
        GenerationContext context = templateContext();
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildInitialMessages(context.systemPrompt(), context);

        PromptExecutionTrace trace = PromptExecutionTrace.fromProviderPayload(messages, context, traceResolver);
        ModelCallLogEntity entity = trace.applyTo(ModelCallLogEntity.builder().taskId(6001L).build());

        assertThat(entity.getPromptTemplateVersionId()).isEqualTo(5001L);
        assertThat(entity.getPromptTemplateCode()).isEqualTo("trace-template");
        assertThat(entity.getPromptTemplateVersionNo()).isEqualTo(1);
        assertThat(entity.getSystemPromptSha256()).isNotBlank();
        assertThat(entity.getUserPromptSha256()).isNotBlank();
        assertThat(entity.getCombinedPromptFingerprint()).isNotBlank();
    }

    @Test
    void noTemplateCallUsesExplicitNoTemplateSemantics() {
        GenerationContext context = new GenerationContext(
                "hello", "App", "WEB_APP", "HTML",
                3001L, 2001L, 6002L, null,
                "openai", "gpt", null, null,
                "default system");
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildInitialMessages(context.systemPrompt(), context);

        PromptExecutionTrace trace = PromptExecutionTrace.noTemplateFromProviderPayload(messages, context);
        ModelCallLogEntity entity = trace.applyTo(ModelCallLogEntity.builder().taskId(6002L).build());

        assertThat(entity.getPromptTemplateVersionId()).isNull();
        assertThat(entity.getPromptTemplateCode()).isNull();
        assertThat(entity.getPromptTemplateVersionNo()).isNull();
        assertThat(entity.getSystemPromptSha256()).isNotBlank();
        assertThat(entity.getUserPromptSha256()).isNotBlank();
        assertThat(entity.getCombinedPromptFingerprint()).isNotBlank();
    }

    private static GenerationContext templateContext() {
        return new GenerationContext(
                "hello", "App", "WEB_APP", "HTML",
                3001L, 2001L, 6001L, null,
                null, null, null, null,
                "CF_RUNTIME_TEMPLATE_SYSTEM_V1",
                "CF_RUNTIME_TEMPLATE_USER_V1_hello",
                4001L, 5001L, "trace-template", 1);
    }
}
