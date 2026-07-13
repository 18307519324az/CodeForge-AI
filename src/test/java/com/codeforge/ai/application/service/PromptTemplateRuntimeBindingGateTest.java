package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionUpdateRequest;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateRequest;
import com.codeforge.ai.application.generation.AiCodegenPromptBuilder;
import com.codeforge.ai.application.generation.CodeGenerationAiService;
import com.codeforge.ai.application.task.GenerationTaskRequestPayloadSupport;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.ModelCallPhase;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.generation.validation.ArtifactValidationResult;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateStatus;
import com.codeforge.ai.domain.prompt.model.PromptFingerprintHasher;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.ai.OpenAiCompatibleModelGateway;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GenerationTaskPersistsTemplateAndVersionColumnsTest {

    @Test
    void shouldPersistPinnedTemplateColumnsOnEnqueue() {
        GenerationTaskEntityMapper generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        AiAppEntityMapper appMapper = mock(AiAppEntityMapper.class);
        given(appMapper.selectOneById(3001L)).willReturn(RuntimeBindingFixtures.app());
        GenerationTaskApplicationService service = GateTestFixtures.generationTaskService(
                appMapper, templateMapper, versionMapper, generationTaskEntityMapper,
                mock(GenerationRecordEntityMapper.class));

        given(versionMapper.selectOneById(5001L)).willReturn(RuntimeBindingFixtures.publishedV1());
        given(templateMapper.selectOneById(4001L)).willReturn(RuntimeBindingFixtures.publishedTemplate());

        ArgumentCaptor<GenerationTaskEntity> taskCaptor = ArgumentCaptor.forClass(GenerationTaskEntity.class);
        doAnswer(invocation -> {
            GenerationTaskEntity entity = invocation.getArgument(0);
            entity.setId(88L);
            return 1;
        }).when(generationTaskEntityMapper).insertTask(taskCaptor.capture());

        GenerationTaskCreateRequest request = RuntimeBindingFixtures.createRequest(5001L);
        ReflectionTestUtils.invokeMethod(service, "enqueueTask",
                new CurrentUser(2001L, "editor", List.of("USER")), request);

        assertThat(taskCaptor.getValue().getPromptTemplateId()).isEqualTo(4001L);
        assertThat(taskCaptor.getValue().getPromptTemplateVersionId()).isEqualTo(5001L);
    }
}

class GenerationTaskWithoutTemplatePersistsNullTemplateColumnsTest {

    @Test
    void shouldPersistNullTemplateColumnsWhenNoTemplateSelected() {
        GenerationTaskEntityMapper generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        AiAppEntityMapper appMapper = mock(AiAppEntityMapper.class);
        given(appMapper.selectOneById(3001L)).willReturn(RuntimeBindingFixtures.app());
        GenerationTaskApplicationService service = GateTestFixtures.generationTaskService(
                appMapper,
                mock(PromptTemplateEntityMapper.class),
                mock(PromptTemplateVersionEntityMapper.class),
                generationTaskEntityMapper,
                mock(GenerationRecordEntityMapper.class));

        ArgumentCaptor<GenerationTaskEntity> taskCaptor = ArgumentCaptor.forClass(GenerationTaskEntity.class);
        doAnswer(invocation -> {
            GenerationTaskEntity entity = invocation.getArgument(0);
            entity.setId(89L);
            return 1;
        }).when(generationTaskEntityMapper).insertTask(taskCaptor.capture());

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("plain requirement");

        ReflectionTestUtils.invokeMethod(service, "enqueueTask",
                new CurrentUser(2001L, "editor", List.of("USER")), request);

        assertThat(taskCaptor.getValue().getPromptTemplateId()).isNull();
        assertThat(taskCaptor.getValue().getPromptTemplateVersionId()).isNull();
    }
}

class SyncGenerationLoadsExactPinnedVersionTest {

    @Test
    void shouldRenderPinnedV1EvenWhenV2IsLatest() {
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateExecutionResolver resolver = new PromptTemplateExecutionResolver(templateMapper, versionMapper);
        given(versionMapper.selectOneById(5001L)).willReturn(RuntimeBindingFixtures.publishedV1());
        given(templateMapper.selectOneById(4001L)).willReturn(RuntimeBindingFixtures.publishedTemplate());

        var resolved = resolver.resolvePinned(4001L, 5001L, "hello", Map.of());

        assertThat(resolved.renderedSystemPrompt()).contains("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
        assertThat(resolved.renderedUserPrompt()).contains("CF_RUNTIME_TEMPLATE_USER_V1_hello");
        assertThat(resolved.renderedSystemPrompt()).doesNotContain("CF_RUNTIME_TEMPLATE_SYSTEM_V2");
    }
}

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V2LatestButGatewayReceivesV1SystemPromptTest {

    @Mock private ModelGatewayInvoker invoker;
    @Mock private AiGeneratedProjectParser parser;
    @Spy private GeneratedArtifactValidator artifactValidator = new GeneratedArtifactValidator();
    @Mock private PromptResourceLoader promptLoader;
    @InjectMocks private CodeGenerationAiService aiService;

    @BeforeEach
    void setUp() {
        given(artifactValidator.validate(any(), anyString())).willReturn(ArtifactValidationResult.valid());
    }

    @Test
    void shouldSendV1SystemPromptToGateway() throws Exception {
        ReflectionTestUtils.setField(aiService, "configuredMaxTokens", 8192);
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), anyInt(), any()))
                .willReturn(RuntimeBindingFixtures.validChatResult());
        given(parser.parse(any(), any())).willReturn(new com.codeforge.ai.domain.generation.GeneratedProject(
                "demo", "demo", "WEB_APP", "hello",
                List.of(new com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile(
                        "index.html", "index.html", "<html><body>ok</body></html>"))));

        ArgumentCaptor<List<ModelMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        aiService.generate(context);
        verify(invoker).streamWithAiProvidersOnly(messagesCaptor.capture(), any(), any(), anyInt(), any());

        String system = messagesCaptor.getValue().getFirst().content();
        assertThat(system).contains("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
        assertThat(system).doesNotContain("CF_RUNTIME_TEMPLATE_SYSTEM_V2");
    }
}

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V2LatestButGatewayReceivesV1UserPromptTest {

    @Mock private ModelGatewayInvoker invoker;
    @Mock private AiGeneratedProjectParser parser;
    @Spy private GeneratedArtifactValidator artifactValidator = new GeneratedArtifactValidator();
    @Mock private PromptResourceLoader promptLoader;
    @InjectMocks private CodeGenerationAiService aiService;

    @BeforeEach
    void setUp() {
        given(artifactValidator.validate(any(), anyString())).willReturn(ArtifactValidationResult.valid());
    }

    @Test
    void shouldSendV1UserPromptToGateway() throws Exception {
        ReflectionTestUtils.setField(aiService, "configuredMaxTokens", 8192);
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), anyInt(), any()))
                .willReturn(RuntimeBindingFixtures.validChatResult());
        given(parser.parse(any(), any())).willReturn(new com.codeforge.ai.domain.generation.GeneratedProject(
                "demo", "demo", "WEB_APP", "hello",
                List.of(new com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile(
                        "index.html", "index.html", "<html><body>ok</body></html>"))));

        ArgumentCaptor<List<ModelMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        aiService.generate(context);
        verify(invoker).streamWithAiProvidersOnly(messagesCaptor.capture(), any(), any(), anyInt(), any());

        String user = messagesCaptor.getValue().get(1).content();
        assertThat(user).contains("CF_RUNTIME_TEMPLATE_USER_V1_hello");
        assertThat(user).doesNotContain("CF_RUNTIME_TEMPLATE_USER_V2_");
    }
}

class GatewayInputDoesNotContainV2MarkerTest {

    @Test
    void shouldNotContainAnyV2MarkersInOutgoingMessages() {
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildInitialMessages(context.systemPrompt(), context);
        String combined = messages.stream().map(ModelMessage::content).reduce("", String::concat);
        assertThat(combined).doesNotContain("CF_RUNTIME_TEMPLATE_SYSTEM_V2");
        assertThat(combined).doesNotContain("CF_RUNTIME_TEMPLATE_USER_V2_");
    }
}

class ModelCallFingerprintMatchesRenderedV1Test {

    @Test
    void shouldMatchRenderedV1FingerprintFromOutgoingMessages() {
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildInitialMessages(context.systemPrompt(), context);
        var rendered = PromptFingerprintHasher.hash(context.systemPrompt(), context.renderedUserPrompt());
        var outgoing = PromptFingerprintHasher.fromMessages(messages);
        assertThat(outgoing.combined()).isEqualTo(rendered.combined());
        assertThat(context.promptTemplateVersionId()).isEqualTo(5001L);
    }
}

class ModelCallFingerprintDoesNotMatchRenderedV2Test {

    @Test
    void shouldNotMatchV2FingerprintForPinnedV1Messages() {
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        List<ModelMessage> v1Messages = AiCodegenPromptBuilder.buildInitialMessages(context.systemPrompt(), context);
        String v2System = "CF_RUNTIME_TEMPLATE_SYSTEM_V2";
        String v2User = "CF_RUNTIME_TEMPLATE_USER_V2_hello";
        var v1Fingerprint = PromptFingerprintHasher.fromMessages(v1Messages);
        var v2Fingerprint = PromptFingerprintHasher.hash(v2System, v2User);
        assertThat(v1Fingerprint.combined()).isNotEqualTo(v2Fingerprint.combined());
    }
}

class MissingPinnedVersionDoesNotFallbackToLatestTest {

    @Test
    void shouldFailWhenPinnedVersionMissing() {
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        given(versionMapper.selectOneById(9999L)).willReturn(null);
        PromptTemplateExecutionResolver resolver = new PromptTemplateExecutionResolver(
                mock(PromptTemplateEntityMapper.class), versionMapper);

        assertThatThrownBy(() -> resolver.resolvePinned(4001L, 9999L, "hello", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Prompt 模板版本不存在");
    }
}

class PublishedTemplateVersionCannotBeMutatedTest {

    @Test
    void shouldRejectPublishedVersionMutationViaEnsureDraftVersionContract() {
        PromptTemplateApplicationService service = new PromptTemplateApplicationService(
                mock(PromptTemplateEntityMapper.class),
                mock(PromptTemplateVersionEntityMapper.class),
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                mock(WorkspaceAccessService.class),
                mock(AuditLogWriter.class),
                new ObjectMapper());
        PromptTemplateVersionEntity published = PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .publishedAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "ensureDraftVersion", published))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已发布版本不可修改，请创建新版本");
    }
}

class CompactRetryKeepsPinnedTemplateVersionTest {

    @Test
    void shouldKeepPinnedTemplateVersionInContextDuringCompactRetry() {
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildCompactMessages(context.systemPrompt(), context);
        assertThat(messages.getFirst().content()).contains("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
        assertThat(context.promptTemplateVersionId()).isEqualTo(5001L);
    }
}

class JsonRetryKeepsPinnedTemplateVersionTest {

    @Test
    void shouldKeepPinnedTemplateVersionInRetryMessages() {
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildRetryMessages(context.systemPrompt(), context);
        assertThat(messages.getFirst().content()).contains("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
        assertThat(context.promptTemplateVersionId()).isEqualTo(5001L);
    }
}

class RepairRetryKeepsPinnedTemplateVersionTest {

    @Test
    void shouldKeepPinnedTemplateVersionInRepairMessages() {
        GenerationContext context = RuntimeBindingFixtures.templateContext();
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildArtifactRepairMessages(
                context.systemPrompt(), context, "missing index.html");
        assertThat(messages.getFirst().content()).contains("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
        assertThat(context.promptTemplateVersionId()).isEqualTo(5001L);
    }
}

class TaskRetryCopiesTemplateAndVersionColumnsTest {

    @Test
    void shouldCopyPinnedTemplateColumnsOnTaskRetry() {
        GenerationTaskEntityMapper generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        GenerationTaskApplicationService service = RuntimeBindingFixtures.generationTaskService(
                mock(PromptTemplateEntityMapper.class),
                mock(PromptTemplateVersionEntityMapper.class),
                generationTaskEntityMapper);

        GenerationTaskEntity sourceTask = GenerationTaskEntity.builder()
                .id(70L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("FAILED")
                .retryCount(0)
                .promptTemplateId(4001L)
                .promptTemplateVersionId(5001L)
                .requestPayloadJson("{\"requirement\":\"hello\",\"promptTemplateId\":4001,\"promptTemplateVersionId\":5001}")
                .build();
        given(generationTaskEntityMapper.selectOneById(70L)).willReturn(sourceTask);

        ArgumentCaptor<GenerationTaskEntity> retryCaptor = ArgumentCaptor.forClass(GenerationTaskEntity.class);
        doAnswer(invocation -> {
            GenerationTaskEntity entity = invocation.getArgument(0);
            entity.setId(71L);
            return 1;
        }).when(generationTaskEntityMapper).insertTask(retryCaptor.capture());

        service.retryTask(new CurrentUser(2001L, "editor", List.of("USER")), 70L);

        assertThat(retryCaptor.getValue().getPromptTemplateId()).isEqualTo(4001L);
        assertThat(retryCaptor.getValue().getPromptTemplateVersionId()).isEqualTo(5001L);
    }
}

class ArchivedTemplateCannotStartNewTaskTest {

    @Test
    void shouldRejectArchivedTemplateForNewTask() {
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        GenerationTaskApplicationService service = RuntimeBindingFixtures.generationTaskService(
                templateMapper, versionMapper, mock(GenerationTaskEntityMapper.class));

        given(versionMapper.selectOneById(5001L)).willReturn(RuntimeBindingFixtures.publishedV1());
        PromptTemplateEntity archived = RuntimeBindingFixtures.publishedTemplate();
        archived.setStatus(PromptTemplateStatus.ARCHIVED.name());
        given(templateMapper.selectOneById(4001L)).willReturn(archived);

        GenerationTaskCreateRequest request = RuntimeBindingFixtures.createRequest(5001L);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolvePromptTemplateVersion",
                new CurrentUser(2001L, "editor", List.of("USER")), 1001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Prompt 模板未发布，无法用于生产生成");
    }
}

class AcceptedTaskCanStillResolveItsPinnedHistoricalVersionTest {

    @Test
    void shouldResolveArchivedTemplateVersionForAcceptedTaskExecution() {
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        PromptTemplateExecutionResolver resolver = new PromptTemplateExecutionResolver(templateMapper, versionMapper);

        PromptTemplateEntity archived = RuntimeBindingFixtures.publishedTemplate();
        archived.setStatus(PromptTemplateStatus.ARCHIVED.name());
        given(versionMapper.selectOneById(5001L)).willReturn(RuntimeBindingFixtures.publishedV1());
        given(templateMapper.selectOneById(4001L)).willReturn(archived);

        var resolved = resolver.resolvePinned(4001L, 5001L, "hello", Map.of());
        assertThat(resolved.templateVersionId()).isEqualTo(5001L);
        assertThat(resolved.renderedSystemPrompt()).contains("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
    }
}

class NormalUserCannotReadRenderedSystemPromptTest {

    @Test
    void publishedTemplateUserDetailShouldNotExposeSystemPromptBody() {
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);
        PromptTemplateApplicationService service = new PromptTemplateApplicationService(
                templateMapper, versionMapper,
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                workspaceAccessService,
                mock(AuditLogWriter.class),
                new ObjectMapper());

        given(templateMapper.selectOneById(4001L)).willReturn(RuntimeBindingFixtures.publishedTemplate());
        given(versionMapper.findByTemplateIdAndVersionNo(4001L, 2)).willReturn(RuntimeBindingFixtures.publishedV2());

        var detail = service.getPublishedTemplate(new CurrentUser(2001L, "user", List.of("USER")), 4001L);
        assertThat(detail.publishedVersion()).isNotNull();
        assertThat(detail.toString()).doesNotContain("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
        assertThat(detail.toString()).doesNotContain("systemPrompt");
    }
}

class ModelCallLogDoesNotExposeFullPromptTest {

    @Test
    void modelCallLogEntityShouldOnlyCarryFingerprintFields() {
        ModelCallLogEntity entity = ModelCallLogEntity.builder()
                .promptTemplateVersionId(5001L)
                .combinedPromptFingerprint("abc123")
                .systemPromptSha256("def456")
                .userPromptSha256("ghi789")
                .build();
        assertThat(entity.getCombinedPromptFingerprint()).isNotBlank();
        assertThat(entity.toString()).doesNotContain("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
    }
}

class TemplateCreateWritesAuditTest {

    @Test
    void shouldWriteAuditOnTemplateCreate() {
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateEntityMapper templateMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateVersionEntityMapper versionMapper = mock(PromptTemplateVersionEntityMapper.class);
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);
        PromptTemplateApplicationService service = new PromptTemplateApplicationService(
                templateMapper, versionMapper,
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                workspaceAccessService,
                auditLogWriter,
                new ObjectMapper());

        doAnswer(invocation -> {
            PromptTemplateEntity entity = invocation.getArgument(0);
            entity.setId(4001L);
            return 1;
        }).when(templateMapper).insertTemplate(any());
        doAnswer(invocation -> {
            PromptTemplateVersionEntity entity = invocation.getArgument(0);
            entity.setId(5001L);
            return 1;
        }).when(versionMapper).insertVersion(any());
        given(templateMapper.findByWorkspaceIdAndTemplateName(1001L, "T")).willReturn(null);
        given(templateMapper.selectOneById(4001L)).willReturn(RuntimeBindingFixtures.publishedTemplate());
        given(versionMapper.selectOneById(5001L)).willReturn(RuntimeBindingFixtures.publishedV1());

        var request = new com.codeforge.ai.application.dto.prompt.PromptTemplateCreateRequest();
        request.setWorkspaceId(1001L);
        request.setTemplateName("T");
        request.setTemplateScene("APP_GENERATION");
        request.setSystemPrompt("CF_RUNTIME_TEMPLATE_SYSTEM_V1");
        request.setUserPrompt("CF_RUNTIME_TEMPLATE_USER_V1_{{requirement}}");
        service.createTemplate(new CurrentUser(2001L, "editor", List.of("USER")), request);

        ArgumentCaptor<AuditLogEntity> auditCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogWriter).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionCode()).isEqualTo("PROMPT_TEMPLATE_CREATE");
    }
}

class TemplateAuditDoesNotContainPromptContentTest {

    @Test
    void auditDetailShouldNotContainPromptBody() {
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PromptTemplateApplicationService service = new PromptTemplateApplicationService(
                mock(PromptTemplateEntityMapper.class),
                mock(PromptTemplateVersionEntityMapper.class),
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                mock(WorkspaceAccessService.class),
                auditLogWriter,
                new ObjectMapper());

        String detail = (String) ReflectionTestUtils.invokeMethod(
                service, "buildTemplateAuditDetail", 4001L, 5001L, "PROMPT_TEMPLATE_VERSION_PUBLISH", 1, false);
        assertThat(detail).contains("templateVersionId");
        assertThat(detail).doesNotContain("CF_RUNTIME_TEMPLATE_SYSTEM");
        assertThat(detail).doesNotContain("systemPrompt");
    }
}

final class RuntimeBindingFixtures {

    private RuntimeBindingFixtures() {
    }

    static PromptTemplateEntity publishedTemplate() {
        return PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .templateName("Runtime Template")
                .status(PromptTemplateStatus.PUBLISHED.name())
                .currentVersionNo(2)
                .build();
    }

    static PromptTemplateVersionEntity publishedV1() {
        return PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .systemPrompt("CF_RUNTIME_TEMPLATE_SYSTEM_V1")
                .userPrompt("CF_RUNTIME_TEMPLATE_USER_V1_{{requirement}}")
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build();
    }

    static PromptTemplateVersionEntity publishedV2() {
        return PromptTemplateVersionEntity.builder()
                .id(5002L)
                .templateId(4001L)
                .versionNo(2)
                .systemPrompt("CF_RUNTIME_TEMPLATE_SYSTEM_V2")
                .userPrompt("CF_RUNTIME_TEMPLATE_USER_V2_{{requirement}}")
                .publishedAt(LocalDateTime.of(2026, 7, 2, 10, 0))
                .build();
    }

    static AiAppEntity app() {
        return AiAppEntity.builder().id(3001L).workspaceId(1001L).name("Demo").appType("WEB_APP").build();
    }

    static GenerationTaskCreateRequest createRequest(Long versionId) {
        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("hello");
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(versionId);
        request.setTemplateVariables(Map.of("requirement", "hello"));
        return request;
    }

    static GenerationContext templateContext() {
        return new GenerationContext(
                "hello",
                "Demo",
                "WEB_APP",
                "HTML",
                3001L,
                2001L,
                88L,
                null,
                null,
                null,
                null,
                null,
                "CF_RUNTIME_TEMPLATE_SYSTEM_V1",
                "CF_RUNTIME_TEMPLATE_USER_V1_hello",
                4001L,
                5001L,
                "Runtime Template",
                1);
    }

    static ModelChatResult validChatResult() {
        return ModelChatResult.success(
                "{\"files\":[{\"filePath\":\"index.html\",\"fileName\":\"index.html\",\"content\":\"<html></html>\"}]}",
                "stop",
                10L,
                20L,
                30L,
                100L,
                "deepseek",
                "deepseek-chat");
    }

    static GenerationTaskApplicationService generationTaskService(
            PromptTemplateEntityMapper templateMapper,
            PromptTemplateVersionEntityMapper versionMapper,
            GenerationTaskEntityMapper taskMapper) {
        AiAppEntityMapper appMapper = mock(AiAppEntityMapper.class);
        given(appMapper.selectOneById(3001L)).willReturn(app());
        return GateTestFixtures.generationTaskService(appMapper, templateMapper, versionMapper, taskMapper,
                mock(GenerationRecordEntityMapper.class));
    }
}
