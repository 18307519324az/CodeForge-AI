package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeneratedFilePersistenceTest {

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
    private final Path storageRoot = Path.of(".local-storage", "apps", "99001");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "forceRuleOnly", false);

        app = AiAppEntity.builder().id(99001L).workspaceId(1001L).name("AI Direct App").appType("WEB_APP").build();
        task = GenerationTaskEntity.builder().id(88001L).workspaceId(1001L).appId(99001L).taskType("RULE_GENERATION").build();

        given(appVersionEntityMapper.findByAppId(99001L)).willReturn(List.of());
        given(generationTaskEntityMapper.updateTerminalState(anyLong(), anyString(), any(), any(), any(), anyLong()))
                .willReturn(1);
        doAnswer(invocation -> {
            AppVersionEntity version = invocation.getArgument(0);
            version.setId(91001L);
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any());
        given(generatedFileEntityMapper.insertFile(any())).willReturn(1);
        given(generationTaskEventEntityMapper.insertEvent(any())).willReturn(1);
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(storageRoot)) {
            try (var paths = Files.walk(storageRoot)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    void shouldWriteAiDirectGeneratedFilesToDisk() throws Exception {
        given(providerSelector.hasConfiguredAiProvider()).willReturn(true);
        given(aiService.generate(any(GenerationContext.class), any())).willReturn(new GeneratedProject(
                "AI Direct App",
                "AI Direct App",
                "WEB_APP",
                "todo",
                List.of(new GeneratedProjectFile(
                        "assets/index.html",
                        "index.html",
                        "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"></head><body>ai-direct-file</body></html>"
                ))
        ));
        given(modelCallLogEntityMapper.findByTaskId(88001L)).willReturn(List.of(
                ModelCallLogEntity.builder()
                        .providerCode("deepseek")
                        .modelName("deepseek-chat")
                        .build()
        ));

        var result = service.executeSync(task, app, "todo", 2001L, "req-ai-direct");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<GeneratedFileEntity> fileCaptor = ArgumentCaptor.forClass(GeneratedFileEntity.class);
        verify(generatedFileEntityMapper, atLeastOnce()).insertFile(fileCaptor.capture());
        Path writtenPath = Path.of(fileCaptor.getAllValues().getFirst().getStoragePath());
        assertThat(Files.exists(writtenPath)).isTrue();
        assertThat(Files.readString(writtenPath, StandardCharsets.UTF_8)).contains("ai-direct-file");
    }

    @Test
    void shouldWriteRuleOnlyGeneratedFilesToDisk() throws Exception {
        ReflectionTestUtils.setField(service, "forceRuleOnly", true);
        given(providerSelector.hasConfiguredAiProvider()).willReturn(false);
        given(providerSelector.selectRuleProvider()).willReturn(ModelProviderEntity.builder()
                .id(3L)
                .providerCode("rule")
                .defaultModel("rule-based")
                .apiProtocol("RULE")
                .build());

        var result = service.executeSync(task, app, "生成一个待办页面", 2001L, "req-rule-only");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<GeneratedFileEntity> fileCaptor = ArgumentCaptor.forClass(GeneratedFileEntity.class);
        verify(generatedFileEntityMapper, atLeastOnce()).insertFile(fileCaptor.capture());
        Path writtenPath = Path.of(fileCaptor.getAllValues().getFirst().getStoragePath());
        assertThat(Files.exists(writtenPath)).isTrue();
    }

    @Test
    void shouldWriteRuleFallbackGeneratedFilesToDisk() throws Exception {
        ReflectionTestUtils.setField(service, "forceRuleOnly", false);
        given(providerSelector.hasConfiguredAiProvider()).willReturn(true);
        given(providerSelector.selectRuleProvider()).willReturn(ModelProviderEntity.builder()
                .id(3L)
                .providerCode("rule")
                .defaultModel("rule-based")
                .apiProtocol("RULE")
                .build());
        given(aiService.generate(any(GenerationContext.class), any()))
                .willThrow(new RuntimeException("所有模型供应商调用均失败，最后错误: timeout"));

        var result = service.executeSync(task, app, "生成一个待办页面", 2001L, "req-rule-fallback");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<GeneratedFileEntity> fileCaptor = ArgumentCaptor.forClass(GeneratedFileEntity.class);
        verify(generatedFileEntityMapper, atLeastOnce()).insertFile(fileCaptor.capture());
        Path writtenPath = Path.of(fileCaptor.getAllValues().getFirst().getStoragePath());
        assertThat(Files.exists(writtenPath)).isTrue();
    }
}
