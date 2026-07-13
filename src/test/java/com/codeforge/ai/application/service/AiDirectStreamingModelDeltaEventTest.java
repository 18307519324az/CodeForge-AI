package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.progress.ModelGenerationProgress;
import com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiDirectStreamingModelDeltaEventTest {

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
        given(appVersionEntityMapper.findByAppId(3001L)).willReturn(List.of());
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);
        lenient().when(modelCallLogEntityMapper.findByTaskId(6001L)).thenReturn(java.util.Collections.emptyList());
        doAnswer(invocation -> {
            com.codeforge.ai.domain.app.entity.AppVersionEntity version = invocation.getArgument(0);
            version.setId(9001L);
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any());
        given(generatedFileEntityMapper.insertFile(any())).willReturn(1);
        lenient().when(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldPersistSafeModelDeltaDuringGeneration() {
        AiAppEntity app = AiAppEntity.builder().id(3001L).workspaceId(1001L).name("Demo").appType("WEB_APP").build();
        GenerationTaskEntity task = GenerationTaskEntity.builder().id(6001L).workspaceId(1001L).appId(3001L).build();

        doAnswer(invocation -> {
            ModelGenerationProgressListener listener = invocation.getArgument(1);
            listener.onProgress(new ModelGenerationProgress(1, 2048L, 12L, 1500L));
            listener.onProgress(new ModelGenerationProgress(1, 4096L, 24L, 3000L));
            return sampleProject();
        }).when(aiService).generate(any(GenerationContext.class), any(ModelGenerationProgressListener.class));

        service.executeSync(task, app, "生成待办", 2001L, "req-stream");

        ArgumentCaptor<com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity> captor =
                ArgumentCaptor.forClass(com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity.class);
        org.mockito.Mockito.verify(generationTaskEventEntityMapper, org.mockito.Mockito.atLeastOnce()).insertEvent(captor.capture());

        List<com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity> modelDeltaEvents = captor.getAllValues().stream()
                .filter(event -> GenerationTaskEventType.MODEL_DELTA.name().equals(event.getEventType()))
                .toList();

        assertThat(modelDeltaEvents).hasSizeGreaterThanOrEqualTo(2);
        assertThat(modelDeltaEvents.getFirst().getEventPayloadJson()).contains("\"receivedChars\"");
        assertThat(modelDeltaEvents.getFirst().getEventPayloadJson()).doesNotContain("content");
        assertThat(modelDeltaEvents.getFirst().getEventPayloadJson()).doesNotContain("<html");
        assertThat(modelDeltaEvents.getFirst().getEventPayloadJson()).doesNotContain("files");
    }

    private GeneratedProject sampleProject() {
        return new GeneratedProject("Demo", "Demo", "WEB_APP", "req",
                List.of(new GeneratedProjectFile("index.html", "index.html",
                        "<!doctype html><html><body>todo</body></html>")));
    }
}
