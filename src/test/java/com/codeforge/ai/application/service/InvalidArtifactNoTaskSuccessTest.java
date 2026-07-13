package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.validation.ArtifactValidationResult;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class InvalidArtifactNoTaskSuccessTest {

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
    @Mock private GeneratedArtifactValidator artifactValidator;
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
        given(generationRecordEntityMapper.updateResultByTaskId(anyLong(), anyString(), anyString(), anyLong(), anyLong()))
                .willReturn(1);
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.empty());
    }

    @Test
    void shouldMarkTaskFailedWhenArtifactInvalid() {
        given(aiService.generate(any(), any())).willReturn(corruptedProject());
        given(artifactValidator.validate(any(), anyString())).willReturn(ArtifactValidationResult.invalid(
                AiGenerationFailureException.AI_ARTIFACT_INVALID,
                List.of("入口 HTML 缺少 head/body 结构")));

        var app = AiAppEntity.builder().id(1L).workspaceId(1L).name("App").appType("WEB_APP").build();
        var task = GenerationTaskEntity.builder().id(10L).workspaceId(1L).appId(1L).build();

        var result = service.executeSync(task, app, "生成 CRM", 1L, "req-invalid");

        assertThat(result.success()).isFalse();
        verify(generationTaskEntityMapper).updateTerminalState(
                eq(10L), eq(GenerationTaskStatus.FAILED.name()), anyString(), anyString(), any(), eq(1L));
        verify(generationTaskEventEntityMapper, never()).insertEvent(org.mockito.ArgumentMatchers.argThat(
                event -> GenerationTaskEventType.TASK_SUCCESS.name().equals(event.getEventType())));
    }

    private GeneratedProject corruptedProject() {
        return new GeneratedProject("CRM", "CRM", "WEB_APP", "req",
                List.of(new GeneratedProjectFile("index.html", "index.html", "broken")));
    }
}
