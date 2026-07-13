package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PreviewAfterAiDirectGenerationTest {

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

    private AiAppEntity app;
    private GenerationTaskEntity task;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "forceRuleOnly", false);

        app = AiAppEntity.builder().id(3001L).workspaceId(1001L).name("工单系统").appType("WEB_APP").build();
        task = GenerationTaskEntity.builder().id(6001L).workspaceId(1001L).appId(3001L).taskType("RULE_GENERATION").build();

        given(providerSelector.hasConfiguredAiProvider()).willReturn(true);
        given(appVersionEntityMapper.findByAppId(3001L)).willReturn(List.of());
        given(generationTaskEntityMapper.updateTerminalState(anyLong(), anyString(), any(), any(), any(), anyLong()))
                .willReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            AppVersionEntity version = invocation.getArgument(0);
            version.setId(9001L);
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any());
        given(generatedFileEntityMapper.insertFile(any())).willReturn(1);
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);
        given(modelCallLogEntityMapper.findByTaskId(6001L)).willReturn(List.of(
                ModelCallLogEntity.builder()
                        .providerCode("deepseek")
                        .modelName("deepseek-chat")
                        .generationSource(GenerationSource.AI_DIRECT.code())
                        .fallbackUsed(false)
                        .build()
        ));
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.empty());
    }

    @Test
    void shouldPersistPreviewInfoForAiDirectHtmlGeneration() {
        given(aiService.generate(any(GenerationContext.class), any())).willReturn(new GeneratedProject(
                "工单系统",
                "工单系统",
                "WEB_APP",
                "生成一个售后工单页面",
                List.of(new GeneratedProjectFile(
                        "index.html",
                        "index.html",
                        "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>工单系统</title></head><body></body></html>"
                ))
        ));

        var result = service.executeSync(task, app, "生成一个售后工单页面", 2001L, "req-preview");

        assertThat(result.success()).isTrue();
        assertThat(result.generationSource()).isEqualTo(GenerationSource.AI_DIRECT);
        assertThat(result.providerCode()).isEqualTo("deepseek");
        assertThat(result.modelName()).isEqualTo("deepseek-chat");

        verify(appVersionEntityMapper).updatePreviewInfo(
                9001L,
                "/api/v1/static-preview/9001/index.html",
                "READY",
                2001L
        );

        ArgumentCaptor<GeneratedFileEntity> fileCaptor = ArgumentCaptor.forClass(GeneratedFileEntity.class);
        verify(generatedFileEntityMapper).insertFile(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getFilePath()).isEqualTo("index.html");
        assertThat(fileCaptor.getValue().getFileContent()).contains("工单系统");
    }
}
