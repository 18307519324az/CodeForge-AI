package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GenerationFallbackBehaviorTest {

    @Mock private CodeGenerationAiService aiService;
    @Mock private ModelProviderSelector providerSelector;
    @Mock private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private GenerationTaskStreamRegistry generationTaskStreamRegistry;
    @Mock private GenerationRecordEntityMapper generationRecordEntityMapper;
    @Mock private com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper aiAppEntityMapper;
    @Mock private com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper appVersionEntityMapper;
    @Mock private com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper generatedFileEntityMapper;
    @Mock private com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper modelCallLogEntityMapper;
    @Mock private PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Spy private GeneratedArtifactValidator artifactValidator = new GeneratedArtifactValidator();
    @Spy private PublicGenerationStreamEventMapper publicGenerationStreamEventMapper =
            new PublicGenerationStreamEventMapper(new com.fasterxml.jackson.databind.ObjectMapper());

    @InjectMocks
    private AiDirectGenerationApplicationService service;

    @BeforeEach
    void setUp() {
        lenient().when(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    void shouldFailTaskWhenParserFailsInsteadOfMarkingAiDirect() {
        ReflectionTestUtils.setField(service, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(service, "forceRuleOnly", false);
        given(providerSelector.hasConfiguredAiProvider()).willReturn(true);
        given(aiService.generate(any(), any())).willThrow(AiGenerationFailureException.invalidJson("parse failed", java.util.Map.of()));
        given(generationTaskEntityMapper.updateTerminalState(any(), any(), any(), any(), any(), any())).willReturn(1);
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);

        var app = com.codeforge.ai.domain.app.entity.AiAppEntity.builder()
                .id(1L).workspaceId(1L).name("App").appType("WEB_APP").build();
        var task = com.codeforge.ai.domain.task.entity.GenerationTaskEntity.builder()
                .id(10L).workspaceId(1L).appId(1L).build();

        var result = service.executeSync(task, app, "生成 CRM", 1L, "req");

        assertThat(result.success()).isFalse();
        assertThat(result.generationSource()).isNull();
        verify(aiService).generate(any(), any());
        verify(appVersionEntityMapper, never()).insertVersion(any());
    }

    @Test
    void shouldSkipAiWhenForceRuleOnly() {
        ReflectionTestUtils.setField(service, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(service, "forceRuleOnly", true);
        given(providerSelector.selectRuleProvider()).willReturn(
                com.codeforge.ai.domain.model.entity.ModelProviderEntity.builder()
                        .id(2L).providerCode("rule").defaultModel("rule-based").apiProtocol("RULE_BASED").build());

        var app = com.codeforge.ai.domain.app.entity.AiAppEntity.builder()
                .id(1L).workspaceId(1L).name("App").appType("WEB_APP").build();
        var task = com.codeforge.ai.domain.task.entity.GenerationTaskEntity.builder()
                .id(10L).workspaceId(1L).appId(1L).build();
        given(appVersionEntityMapper.findByAppId(1L)).willReturn(java.util.List.of());
        given(generationTaskEntityMapper.updateTerminalState(any(), any(), any(), any(), any(), any())).willReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            com.codeforge.ai.domain.app.entity.AppVersionEntity version = invocation.getArgument(0);
            version.setId(100L);
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any());
        given(generatedFileEntityMapper.insertFile(any())).willReturn(1);
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);
        given(modelCallLogEntityMapper.insertCallLog(any())).willReturn(1);

        var result = service.executeSync(task, app, "生成 CRM", 1L, "req");

        assertThat(result.generationSource()).isEqualTo(GenerationSource.RULE_ONLY);
        verify(aiService, never()).generate(any());
    }
}
