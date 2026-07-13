package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.model.NoAiProviderAvailableException;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiDirectGenerationApplicationServiceTest {

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
    @Mock private com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Spy private GeneratedArtifactValidator artifactValidator = new GeneratedArtifactValidator();
    @Spy private PublicGenerationStreamEventMapper publicGenerationStreamEventMapper =
            new PublicGenerationStreamEventMapper(new ObjectMapper());

    @InjectMocks
    private AiDirectGenerationApplicationService service;

    private AiAppEntity app;
    private GenerationTaskEntity task;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "forceRuleOnly", false);

        app = AiAppEntity.builder().id(3001L).workspaceId(1001L).name("Demo App").appType("WEB_APP").build();
        task = GenerationTaskEntity.builder().id(6001L).workspaceId(1001L).appId(3001L).taskType("RULE_GENERATION").build();

        given(appVersionEntityMapper.findByAppId(3001L)).willReturn(List.of());
        given(generationTaskEntityMapper.updateTerminalState(anyLong(), anyString(), any(), any(), any(), anyLong()))
                .willReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            com.codeforge.ai.domain.app.entity.AppVersionEntity version = invocation.getArgument(0);
            version.setId(9001L);
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any());
        given(generatedFileEntityMapper.insertFile(any())).willReturn(1);
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);
        lenient().when(modelCallLogEntityMapper.insertCallLog(any())).thenReturn(1);
        lenient().when(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldUseAiDirectWhenModelCallSucceeds() {
        given(providerSelector.hasConfiguredAiProvider()).willReturn(true);
        given(aiService.generate(any(GenerationContext.class), any())).willReturn(sampleProject("AI 页面"));
        given(modelCallLogEntityMapper.findByTaskId(6001L)).willReturn(List.of(
                com.codeforge.ai.domain.model.entity.ModelCallLogEntity.builder()
                        .providerCode("openai")
                        .modelName("gpt-4.1-mini")
                        .generationSource(GenerationSource.AI_DIRECT.code())
                        .fallbackUsed(false)
                        .build()
        ));

        var result = service.executeSync(task, app, "生成待办页面", 2001L, "req-ai");

        assertThat(result.success()).isTrue();
        assertThat(result.generationSource()).isEqualTo(GenerationSource.AI_DIRECT);
        assertThat(result.fallbackUsed()).isFalse();
        verify(aiService).generate(any(GenerationContext.class), any());
    }

    @Test
    void shouldKeepAiDirectWhenCallLogReadFails() {
        given(providerSelector.hasConfiguredAiProvider()).willReturn(true);
        given(aiService.generate(any(GenerationContext.class), any())).willReturn(sampleProject("AI Todo"));
        given(modelCallLogEntityMapper.findByTaskId(6001L))
                .willThrow(new RuntimeException("column prompt_template_version_id not found"));
        given(providerSelector.selectAiProviders()).willReturn(List.of(
                ModelProviderEntity.builder()
                        .id(3L)
                        .providerCode("deepseek")
                        .defaultModel("deepseek-chat")
                        .apiProtocol("OPENAI_COMPATIBLE")
                        .build()
        ));

        var result = service.executeSync(task, app, "生成待办页面", 2001L, "req-log-read-fail");

        assertThat(result.success()).isTrue();
        assertThat(result.generationSource()).isEqualTo(GenerationSource.AI_DIRECT);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.providerCode()).isEqualTo("deepseek");
        assertThat(result.modelName()).isEqualTo("deepseek-chat");
        verify(aiService).generate(any(GenerationContext.class), any());
    }

    @Test
    void shouldFailTaskWhenAiParseFails() {
        given(providerSelector.hasConfiguredAiProvider()).willReturn(true);
        given(generationTaskEntityMapper.updateTerminalState(anyLong(), anyString(), any(), any(), any(), anyLong()))
                .willReturn(1);
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);
        given(aiService.generate(any(GenerationContext.class), any()))
                .willThrow(AiGenerationFailureException.invalidJson("AI 输出解析失败", java.util.Map.of()));

        var result = service.executeSync(task, app, "生成待办页面", 2001L, "req-fallback");

        assertThat(result.success()).isFalse();
        assertThat(result.generationSource()).isNull();
        verify(modelCallLogEntityMapper, org.mockito.Mockito.never()).insertCallLog(any());
    }

    @Test
    void shouldUseRuleOnlyWhenAiNotConfigured() {
        given(providerSelector.hasConfiguredAiProvider()).willReturn(false);
        given(providerSelector.selectRuleProvider()).willReturn(ruleProvider());

        var result = service.executeSync(task, app, "生成待办页面", 2001L, "req-rule");

        assertThat(result.success()).isTrue();
        assertThat(result.generationSource()).isEqualTo(GenerationSource.RULE_ONLY);
        assertThat(result.fallbackUsed()).isFalse();

        ArgumentCaptor<com.codeforge.ai.domain.model.entity.ModelCallLogEntity> captor =
                ArgumentCaptor.forClass(com.codeforge.ai.domain.model.entity.ModelCallLogEntity.class);
        verify(modelCallLogEntityMapper).insertCallLog(captor.capture());
        assertThat(captor.getValue().getGenerationSource()).isEqualTo(GenerationSource.RULE_ONLY.code());
        assertThat(captor.getValue().getFallbackUsed()).isFalse();
    }

    @Test
    void shouldUseRuleOnlyWhenForceRuleOnlyEnabled() {
        ReflectionTestUtils.setField(service, "forceRuleOnly", true);
        given(providerSelector.selectRuleProvider()).willReturn(ruleProvider());

        var result = service.executeSync(task, app, "生成待办页面", 2001L, "req-force");

        assertThat(result.generationSource()).isEqualTo(GenerationSource.RULE_ONLY);
        verify(aiService, org.mockito.Mockito.never()).generate(any());
    }

    private ModelProviderEntity ruleProvider() {
        return ModelProviderEntity.builder()
                .id(2L)
                .providerCode("rule")
                .defaultModel("rule-based")
                .apiProtocol("RULE_BASED")
                .build();
    }

    private GeneratedProject sampleProject(String title) {
        String html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>"
                + title + "</title></head><body><h1>" + title + "</h1></body></html>";
        return new GeneratedProject(title, "Demo", "WEB_APP", "req",
                List.of(new GeneratedProjectFile("index.html", "index.html", html)));
    }
}
