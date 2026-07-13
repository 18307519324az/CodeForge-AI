package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiNoSilentFallbackOnTruncationTest {

    @Mock private CodeGenerationAiService aiService;
    @Mock private ModelProviderSelector providerSelector;
    @Mock private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private GenerationTaskStreamRegistry generationTaskStreamRegistry;
    @Mock private GenerationRecordEntityMapper generationRecordEntityMapper;
    @Mock private AiAppEntityMapper aiAppEntityMapper;
    @Mock private AppVersionEntityMapper appVersionEntityMapper;
    @Mock private GeneratedFileEntityMapper generatedFileEntityMapper;
    @Mock private ModelCallLogEntityMapper modelCallLogEntityMapper;
    @Mock private PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Spy private GeneratedArtifactValidator artifactValidator = new GeneratedArtifactValidator();
    @Spy private PublicGenerationStreamEventMapper publicGenerationStreamEventMapper =
            new PublicGenerationStreamEventMapper(new ObjectMapper());

    @InjectMocks
    private AiDirectGenerationApplicationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "forceRuleOnly", false);
        given(providerSelector.hasConfiguredAiProvider()).willReturn(true);
        given(generationTaskEntityMapper.updateTerminalState(anyLong(), anyString(), any(), any(), any(), anyLong()))
                .willReturn(1);
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.empty());
    }

    @Test
    void shouldNotUseRuleFallbackWhenOutputIsTruncated() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("finishReason", "length");
        metadata.put("outputTokens", 8192);
        given(aiService.generate(any(), any())).willThrow(AiGenerationFailureException.truncated(metadata));

        var app = AiAppEntity.builder().id(1L).workspaceId(1L).name("App").appType("WEB_APP").build();
        var task = GenerationTaskEntity.builder().id(10L).workspaceId(1L).appId(1L).build();

        var result = service.executeSync(task, app, "生成 CRM", 1L, "req-truncated");

        assertThat(result.success()).isFalse();
        assertThat(result.generationSource()).isNull();
        verify(appVersionEntityMapper, never()).insertVersion(any());
        verify(generationTaskEntityMapper).updateTerminalState(
                eq(10L),
                eq(GenerationTaskStatus.FAILED.name()),
                eq(AiGenerationFailureException.AI_OUTPUT_TRUNCATED),
                anyString(),
                any(),
                eq(1L));

        ArgumentCaptor<com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity> captor =
                ArgumentCaptor.forClass(com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity.class);
        verify(generationTaskEventEntityMapper, org.mockito.Mockito.atLeastOnce()).insertEvent(captor.capture());
        assertThat(captor.getAllValues())
                .noneMatch(event -> GenerationTaskEventType.VERSION_CREATED.name().equals(event.getEventType()));
    }
}
