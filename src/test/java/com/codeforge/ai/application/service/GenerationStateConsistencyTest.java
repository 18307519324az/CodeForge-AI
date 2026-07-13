package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenerationStateConsistencyTest {

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
        given(aiService.generate(any(), any())).willReturn(sampleProject("CRM"));
        given(modelCallLogEntityMapper.findByTaskId(10L)).willReturn(List.of(
                ModelCallLogEntity.builder()
                        .providerCode("deepseek")
                        .modelName("deepseek-chat")
                        .generationSource(GenerationSource.AI_DIRECT.code())
                        .fallbackUsed(false)
                        .build()
        ));
        given(appVersionEntityMapper.findByAppId(1L)).willReturn(List.of());
        given(generatedFileEntityMapper.insertFile(any())).willReturn(1);
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);
        given(generationRecordEntityMapper.updateResultByTaskId(anyLong(), anyString(), anyString(), anyLong(), anyLong()))
                .willReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            AppVersionEntity version = invocation.getArgument(0);
            version.setId(100L);
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any());
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.empty());
    }

    @Test
    void shouldPublishVersionCreatedOnlyAfterSuccessTerminalState() {
        given(generationTaskEntityMapper.updateTerminalState(anyLong(), anyString(), any(), any(), any(), anyLong()))
                .willReturn(1);

        var app = AiAppEntity.builder().id(1L).workspaceId(1L).name("App").appType("WEB_APP").build();
        var task = GenerationTaskEntity.builder().id(10L).workspaceId(1L).appId(1L).build();

        var result = service.executeSync(task, app, "生成 CRM", 1L, "req-success");

        assertThat(result.success()).isTrue();
        var inOrder = inOrder(generationTaskEntityMapper, generationTaskEventEntityMapper);
        inOrder.verify(generationTaskEntityMapper).updateTerminalState(
                eq(10L), eq(GenerationTaskStatus.SUCCESS.name()), eq(null), eq(null), any(), eq(1L));
        inOrder.verify(generationTaskEventEntityMapper).insertEvent(org.mockito.ArgumentMatchers.argThat(
                event -> GenerationTaskEventType.VERSION_CREATED.name().equals(event.getEventType())));
        inOrder.verify(generationTaskEventEntityMapper).insertEvent(org.mockito.ArgumentMatchers.argThat(
                event -> GenerationTaskEventType.TASK_SUCCESS.name().equals(event.getEventType())));

        ArgumentCaptor<com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity> captor =
                ArgumentCaptor.forClass(com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity.class);
        org.mockito.Mockito.verify(generationTaskEventEntityMapper, org.mockito.Mockito.atLeastOnce()).insertEvent(captor.capture());
        assertThat(captor.getAllValues())
                .noneMatch(event -> GenerationTaskEventType.TASK_FAILED.name().equals(event.getEventType()));
    }

    @Test
    void shouldNotPublishTaskFailedWhenTerminalStateAlreadySuccess() {
        given(generationTaskEntityMapper.updateTerminalState(anyLong(), anyString(), any(), any(), any(), anyLong()))
                .willAnswer(invocation -> {
                    String status = invocation.getArgument(1);
                    return GenerationTaskStatus.SUCCESS.name().equals(status) ? 1 : 0;
                });
        given(aiAppEntityMapper.updateCurrentVersionId(anyLong(), anyLong(), anyLong()))
                .willThrow(new NullPointerException());

        var app = AiAppEntity.builder().id(1L).workspaceId(1L).name("App").appType("WEB_APP").build();
        var task = GenerationTaskEntity.builder().id(10L).workspaceId(1L).appId(1L).build();

        var result = service.executeSync(task, app, "生成 CRM", 1L, "req-partial");

        assertThat(result.success()).isFalse();
        org.mockito.Mockito.verify(generationTaskEventEntityMapper, never()).insertEvent(
                org.mockito.ArgumentMatchers.argThat(event ->
                        GenerationTaskEventType.TASK_FAILED.name().equals(event.getEventType())));
    }

    private GeneratedProject sampleProject(String title) {
        String html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>"
                + title + "</title></head><body><h1>" + title + "</h1></body></html>";
        return new GeneratedProject(title, "Demo", "WEB_APP", "req",
                List.of(new GeneratedProjectFile("index.html", "index.html", html)));
    }
}
