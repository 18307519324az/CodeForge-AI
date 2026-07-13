package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.prompt.PromptTemplateUserDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserListItemResponse;
import com.codeforge.ai.application.dto.prompt.PublishedPromptTemplateQueryRequest;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateRequest;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.ModelCallPhase;
import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateStatus;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTrace;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.domain.task.entity.GenerationRecordEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.ai.OpenAiCompatibleModelGateway;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GenerationRequestMustBindTemplateVersionTest {

    private GenerationTaskApplicationService generationTaskApplicationService;

    @BeforeEach
    void setUp() {
        generationTaskApplicationService = GateTestFixtures.generationTaskService();
    }

    @Test
    void shouldRejectTemplateIdWithoutVersionId() {
        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");
        request.setPromptTemplateId(4001L);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                generationTaskApplicationService,
                "resolvePromptTemplateVersion",
                new CurrentUser(2001L, "editor", List.of("USER")),
                1001L,
                request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("promptTemplateId 与 promptTemplateVersionId 必须同时提供");
    }

    @Test
    void shouldRejectVersionIdWithoutTemplateId() {
        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");
        request.setPromptTemplateVersionId(5001L);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                generationTaskApplicationService,
                "resolvePromptTemplateVersion",
                new CurrentUser(2001L, "editor", List.of("USER")),
                1001L,
                request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("promptTemplateId 与 promptTemplateVersionId 必须同时提供");
    }
}

class ExplicitV1IsUsedWhenV2IsLatestTest {

    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private GenerationTaskApplicationService generationTaskApplicationService;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        generationTaskApplicationService = GateTestFixtures.generationTaskService(
                promptTemplateEntityMapper, promptTemplateVersionEntityMapper);
    }

    @Test
    void shouldResolvePinnedV1EvenWhenCurrentVersionIsV2() {
        PromptTemplateVersionEntity v1 = PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .systemPrompt("CF_TEMPLATE_VERSION_V1_GATE")
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build();
        given(promptTemplateVersionEntityMapper.selectOneById(5001L)).willReturn(v1);
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.PUBLISHED.name())
                .currentVersionNo(2)
                .build());

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(5001L);

        Object resolved = ReflectionTestUtils.invokeMethod(
                generationTaskApplicationService,
                "resolvePromptTemplateVersion",
                new CurrentUser(2001L, "editor", List.of("USER")),
                1001L,
                request);

        assertThat(resolved).isInstanceOf(PromptTemplateVersionEntity.class);
        assertThat(((PromptTemplateVersionEntity) resolved).getId()).isEqualTo(5001L);
        assertThat(((PromptTemplateVersionEntity) resolved).getVersionNo()).isEqualTo(1);
    }
}

class GenerationTaskPersistsTemplateVersionIdTest {

    private GenerationTaskEntityMapper generationTaskEntityMapper;
    private GenerationRecordEntityMapper generationRecordEntityMapper;
    private AiAppEntityMapper aiAppEntityMapper;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private GenerationTaskApplicationService generationTaskApplicationService;

    @BeforeEach
    void setUp() {
        generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        generationRecordEntityMapper = mock(GenerationRecordEntityMapper.class);
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        generationTaskApplicationService = GateTestFixtures.generationTaskService(
                aiAppEntityMapper,
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                generationTaskEntityMapper,
                generationRecordEntityMapper);
    }

    @Test
    void shouldPersistExplicitTemplateVersionIdOnRecord() {
        given(generationTaskEntityMapper.findRunningTaskId(any())).willReturn(null);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .build());
        given(promptTemplateVersionEntityMapper.selectOneById(5001L)).willReturn(PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .systemPrompt("system")
                .userPrompt("user")
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.PUBLISHED.name())
                .currentVersionNo(2)
                .build());
        doAnswer(invocation -> {
            GenerationTaskEntity entity = invocation.getArgument(0);
            entity.setId(6001L);
            entity.setQueuedAt(LocalDateTime.of(2026, 7, 11, 10, 0));
            return 1;
        }).when(generationTaskEntityMapper).insertTask(any(GenerationTaskEntity.class));

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("minimal static page");
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(5001L);

        GenerationTaskCreateResponse response = generationTaskApplicationService.createTask(
                new CurrentUser(2001L, "editor", List.of("USER")), request);

        ArgumentCaptor<GenerationRecordEntity> recordCaptor = ArgumentCaptor.forClass(GenerationRecordEntity.class);
        verify(generationRecordEntityMapper).insertRecord(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getPromptTemplateVersionId()).isEqualTo(5001L);
        assertThat(response.taskId()).isEqualTo(6001L);
    }
}

class RetryKeepsOriginalTemplateVersionTest {

    private GenerationTaskApplicationService generationTaskApplicationService;
    private GenerationTaskEntityMapper generationTaskEntityMapper;
    private GenerationRecordEntityMapper generationRecordEntityMapper;
    private AiAppEntityMapper aiAppEntityMapper;

    @BeforeEach
    void setUp() {
        generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        generationRecordEntityMapper = mock(GenerationRecordEntityMapper.class);
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        generationTaskApplicationService = GateTestFixtures.generationTaskService(
                aiAppEntityMapper,
                mock(PromptTemplateEntityMapper.class),
                mock(PromptTemplateVersionEntityMapper.class),
                generationTaskEntityMapper,
                generationRecordEntityMapper);
    }

    @Test
    void shouldCopySourceTemplateVersionIdOnTaskRetry() {
        GenerationTaskEntity sourceTask = GenerationTaskEntity.builder()
                .id(6003L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("FAILED")
                .retryCount(0)
                .requestPayloadJson("{\"promptTemplateId\":4001,\"promptTemplateVersionId\":5001}")
                .build();
        given(generationTaskEntityMapper.selectOneById(6003L)).willReturn(sourceTask);
        given(generationRecordEntityMapper.findLatestByTaskId(6003L)).willReturn(GenerationRecordEntity.builder()
                .taskId(6003L)
                .promptTemplateVersionId(5001L)
                .inputSummary("minimal static page")
                .build());
        given(generationTaskEntityMapper.findRunningTaskId(any())).willReturn(null);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .build());
        doAnswer(invocation -> {
            GenerationTaskEntity entity = invocation.getArgument(0);
            entity.setId(6004L);
            entity.setQueuedAt(LocalDateTime.of(2026, 7, 11, 10, 5));
            return 1;
        }).when(generationTaskEntityMapper).insertTask(any(GenerationTaskEntity.class));

        generationTaskApplicationService.retryTask(new CurrentUser(2001L, "editor", List.of("USER")), 6003L);

        ArgumentCaptor<GenerationRecordEntity> recordCaptor = ArgumentCaptor.forClass(GenerationRecordEntity.class);
        verify(generationRecordEntityMapper).insertRecord(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getPromptTemplateVersionId()).isEqualTo(5001L);
    }
}

@ExtendWith(MockitoExtension.class)
class CompactRetryKeepsOriginalTemplateVersionTest {

    @Mock private com.codeforge.ai.domain.generation.model.ModelProviderSelector selector;
    @Mock private com.codeforge.ai.domain.generation.model.ModelGatewayFactory factory;
    @Mock private ModelCallLogEntityMapper callLogMapper;
    @Mock private PromptTemplateTraceResolver promptTemplateTraceResolver;
    @Mock private com.codeforge.ai.domain.generation.model.ProviderCredentialResolver credentialResolver;
    @Mock private OpenAiCompatibleModelGateway openAiGateway;

    @InjectMocks
    private ModelGatewayInvoker invoker;

    @Test
    void shouldKeepPinnedTemplateVersionOnCompactRetryCallLog() {
        given(selector.selectAiProviders()).willReturn(List.of(aiProvider()));
        given(factory.getGateway(any())).willReturn(openAiGateway);
        given(credentialResolver.resolveApiKey(any())).willReturn("test-api-key");
        given(promptTemplateTraceResolver.resolveByTaskId(6001L))
                .willReturn(new PromptTemplateTrace(5001L, "Gate Template", 1));
        doAnswer(invocation -> {
            ModelStreamHandler handler = invocation.getArgument(1);
            handler.onComplete(ModelChatResult.success(
                    "{\"files\":[]}", "stop", 1L, 2L, 3L, 10L, "openai", "gpt-4.1-mini"));
            return null;
        }).when(openAiGateway).streamChatRequest(any(), any());

        GenerationContext context = new GenerationContext(
                "minimal", "App", "WEB_APP", "HTML",
                3001L, 2001L, 6001L, null,
                null, null, null, null, "system");

        invoker.streamWithAiProvidersOnly(
                List.of(ModelMessage.user("hello")),
                context,
                com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener.NOOP,
                1,
                ModelCallPhase.COMPACT_RETRY);

        ArgumentCaptor<ModelCallLogEntity> captor = ArgumentCaptor.forClass(ModelCallLogEntity.class);
        verify(callLogMapper).insertCallLog(captor.capture());
        assertThat(captor.getValue().getPromptTemplateVersionId()).isEqualTo(5001L);
        assertThat(captor.getValue().getPromptTemplateVersionNo()).isEqualTo(1);
        assertThat(captor.getValue().getGenerationSource()).isEqualTo(ModelCallPhase.COMPACT_RETRY.generationSourceCode());
    }

    private ModelProviderEntity aiProvider() {
        return ModelProviderEntity.builder()
                .id(1L)
                .providerCode("openai")
                .apiProtocol("OPENAI_COMPATIBLE")
                .defaultModel("gpt-4.1-mini")
                .build();
    }
}

class ArchivedTemplateVersionCannotStartNewGenerationTest {

    private GenerationTaskApplicationService generationTaskApplicationService;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        generationTaskApplicationService = GateTestFixtures.generationTaskService(
                promptTemplateEntityMapper, promptTemplateVersionEntityMapper);
    }

    @Test
    void shouldRejectArchivedTemplateForNewGeneration() {
        given(promptTemplateVersionEntityMapper.selectOneById(5001L)).willReturn(PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.ARCHIVED.name())
                .currentVersionNo(1)
                .build());

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(5001L);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                generationTaskApplicationService,
                "resolvePromptTemplateVersion",
                new CurrentUser(2001L, "editor", List.of("USER")),
                1001L,
                request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Prompt 模板未发布，无法用于生产生成");
    }
}

class DraftTemplateIsNotVisibleToNormalUserTest {

    private PromptTemplateApplicationService promptTemplateApplicationService;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private WorkspaceAccessService workspaceAccessService;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        promptTemplateApplicationService = new PromptTemplateApplicationService(
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                workspaceAccessService,
                mock(com.codeforge.ai.infrastructure.audit.AuditLogWriter.class),
                new ObjectMapper());
    }

    @Test
    void shouldHideDraftTemplateFromPublishedList() {
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        given(promptTemplateEntityMapper.findAccessibleTemplates(
                eq(List.of(1001L)), eq(null), eq(null), eq("PUBLISHED")))
                .willReturn(List.of());

        PublishedPromptTemplateQueryRequest request = new PublishedPromptTemplateQueryRequest();
        request.setPageNo(1);
        request.setPageSize(10);

        PageResponse<PromptTemplateUserListItemResponse> response = promptTemplateApplicationService.listPublishedTemplates(
                new CurrentUser(2001L, "reader", List.of("USER")), request);

        assertThat(response.records()).isEmpty();
    }
}

class ArchivedTemplateIsNotVisibleToNormalUserTest {

    private PromptTemplateApplicationService promptTemplateApplicationService;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private WorkspaceAccessService workspaceAccessService;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        promptTemplateApplicationService = new PromptTemplateApplicationService(
                promptTemplateEntityMapper,
                mock(PromptTemplateVersionEntityMapper.class),
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                workspaceAccessService,
                mock(com.codeforge.ai.infrastructure.audit.AuditLogWriter.class),
                new ObjectMapper());
    }

    @Test
    void shouldHideArchivedTemplateFromPublishedList() {
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        given(promptTemplateEntityMapper.findAccessibleTemplates(
                eq(List.of(1001L)), eq(null), eq(null), eq("PUBLISHED")))
                .willReturn(List.of());

        PublishedPromptTemplateQueryRequest request = new PublishedPromptTemplateQueryRequest();
        request.setPageNo(1);
        request.setPageSize(10);

        PageResponse<PromptTemplateUserListItemResponse> response = promptTemplateApplicationService.listPublishedTemplates(
                new CurrentUser(2001L, "reader", List.of("USER")), request);

        assertThat(response.records()).isEmpty();
    }

    @Test
    void shouldRejectArchivedTemplateDetailForUser() {
        given(promptTemplateEntityMapper.selectOneById(3L)).willReturn(PromptTemplateEntity.builder()
                .id(3L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.ARCHIVED.name())
                .currentVersionNo(2)
                .build());

        assertThatThrownBy(() -> promptTemplateApplicationService.getPublishedTemplate(
                new CurrentUser(2001L, "reader", List.of("USER")), 3L))
                .isInstanceOf(BusinessException.class);
    }
}

class ForeignUserCannotReadAdminTemplateContentTest {

    private PromptTemplateApplicationService promptTemplateApplicationService;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private WorkspaceAccessService workspaceAccessService;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        promptTemplateApplicationService = new PromptTemplateApplicationService(
                promptTemplateEntityMapper,
                mock(PromptTemplateVersionEntityMapper.class),
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                workspaceAccessService,
                mock(com.codeforge.ai.infrastructure.audit.AuditLogWriter.class),
                new ObjectMapper());
    }

    @Test
    void shouldRejectForeignUserFromPublishedTemplateDetail() {
        CurrentUser foreignUser = new CurrentUser(9001L, "p0userb", List.of("USER"));
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.PUBLISHED.name())
                .build());
        doThrow(new BusinessException(ErrorCode.RESOURCE_FORBIDDEN))
                .when(workspaceAccessService).requireReadAccess(foreignUser, 1001L);

        assertThatThrownBy(() -> promptTemplateApplicationService.getPublishedTemplate(foreignUser, 4001L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    @Test
    void shouldRejectReaderFromAdminVersionListWithFullPrompts() {
        CurrentUser reader = new CurrentUser(2001L, "reader", List.of("USER"));
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.PUBLISHED.name())
                .build());
        doThrow(new BusinessException(ErrorCode.RESOURCE_FORBIDDEN))
                .when(workspaceAccessService).requireEditorAccess(reader, 1001L);

        assertThatThrownBy(() -> promptTemplateApplicationService.listTemplateVersions(reader, 4001L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }
}

class TemplateVersionCannotBelongToAnotherTemplateTest {

    private GenerationTaskApplicationService generationTaskApplicationService;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        generationTaskApplicationService = GateTestFixtures.generationTaskService(
                promptTemplateEntityMapper, promptTemplateVersionEntityMapper);
    }

    @Test
    void shouldRejectCrossTemplateVersionBinding() {
        given(promptTemplateVersionEntityMapper.selectOneById(9001L)).willReturn(PromptTemplateVersionEntity.builder()
                .id(9001L)
                .templateId(4999L)
                .versionNo(1)
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build());

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(9001L);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                generationTaskApplicationService,
                "resolvePromptTemplateVersion",
                new CurrentUser(2001L, "editor", List.of("USER")),
                1001L,
                request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("promptTemplateVersionId 与 promptTemplateId 不匹配");
    }
}

class MissingTemplateVersionReturnsSafeErrorTest {

    private GenerationTaskApplicationService generationTaskApplicationService;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;

    @BeforeEach
    void setUp() {
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        generationTaskApplicationService = GateTestFixtures.generationTaskService(
                mock(PromptTemplateEntityMapper.class), promptTemplateVersionEntityMapper);
    }

    @Test
    void shouldReturnSafeNotFoundWithoutPromptLeak() {
        given(promptTemplateVersionEntityMapper.selectOneById(9999L)).willReturn(null);

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(9999L);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                generationTaskApplicationService,
                "resolvePromptTemplateVersion",
                new CurrentUser(2001L, "editor", List.of("USER")),
                1001L,
                request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Prompt 模板版本不存在")
                .extracting(ex -> ex.getMessage())
                .asString()
                .doesNotContain("CF_TEMPLATE_VERSION")
                .doesNotContain("system prompt");
    }
}

class ModelCallTraceRecordsTemplateVersionTest {

    private PromptTemplateTraceResolver promptTemplateTraceResolver;
    private GenerationRecordEntityMapper generationRecordEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;

    @BeforeEach
    void setUp() {
        generationRecordEntityMapper = mock(GenerationRecordEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateTraceResolver = new PromptTemplateTraceResolver(
                generationRecordEntityMapper,
                mock(com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper.class),
                promptTemplateVersionEntityMapper,
                promptTemplateEntityMapper);
    }

    @Test
    void shouldResolveTraceFromPinnedGenerationRecord() {
        given(generationRecordEntityMapper.findLatestByTaskId(66L)).willReturn(GenerationRecordEntity.builder()
                .taskId(66L)
                .promptTemplateVersionId(5001L)
                .build());
        given(promptTemplateVersionEntityMapper.selectOneById(5001L)).willReturn(PromptTemplateVersionEntity.builder()
                .id(5001L)
                .templateId(4001L)
                .versionNo(1)
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .templateName("Gate Template")
                .build());

        var trace = promptTemplateTraceResolver.resolveByTaskId(66L);

        assertThat(trace.promptTemplateVersionId()).isEqualTo(5001L);
        assertThat(trace.promptTemplateVersionNo()).isEqualTo(1);
        assertThat(trace.promptTemplateCode()).isEqualTo("Gate Template");
    }
}

class PublicErrorDoesNotExposeFullTemplateContentTest {

    private PromptTemplateApplicationService promptTemplateApplicationService;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private WorkspaceAccessService workspaceAccessService;

    @BeforeEach
    void setUp() {
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        promptTemplateApplicationService = new PromptTemplateApplicationService(
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                mock(GenerationRecordEntityMapper.class),
                mock(ModelCallLogEntityMapper.class),
                workspaceAccessService,
                mock(com.codeforge.ai.infrastructure.audit.AuditLogWriter.class),
                new ObjectMapper());
    }

    @Test
    void publishedTemplateDetailShouldNotExposeSystemPrompt() {
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(PromptTemplateEntity.builder()
                .id(1L)
                .workspaceId(1001L)
                .templateName("Gate Template")
                .templateScene("CODE_GEN")
                .status(PromptTemplateStatus.PUBLISHED.name())
                .currentVersionNo(1)
                .remark("remark")
                .build());
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 1))
                .willReturn(PromptTemplateVersionEntity.builder()
                        .id(11L)
                        .templateId(1L)
                        .versionNo(1)
                        .systemPrompt("SECRET_SYSTEM_PROMPT_CF_TEMPLATE_VERSION_V1")
                        .userPrompt("user {{app_name}}")
                        .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                        .build());

        PromptTemplateUserDetailResponse detail = promptTemplateApplicationService.getPublishedTemplate(
                new CurrentUser(2001L, "reader", List.of("USER")), 1L);

        assertThat(detail.publishedVersion().id()).isEqualTo(11L);
        assertThat(detail.toString()).doesNotContain("SECRET_SYSTEM_PROMPT");
        assertThat(detail.toString()).doesNotContain("CF_TEMPLATE_VERSION_V1");
    }
}

final class GateTestFixtures {

    private GateTestFixtures() {
    }

    static GenerationTaskApplicationService generationTaskService() {
        return generationTaskService(
                mock(AiAppEntityMapper.class),
                mock(PromptTemplateEntityMapper.class),
                mock(PromptTemplateVersionEntityMapper.class),
                mock(GenerationTaskEntityMapper.class),
                mock(GenerationRecordEntityMapper.class));
    }

    static GenerationTaskApplicationService generationTaskService(
            PromptTemplateEntityMapper promptTemplateEntityMapper,
            PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper) {
        return generationTaskService(
                mock(AiAppEntityMapper.class),
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                mock(GenerationTaskEntityMapper.class),
                mock(GenerationRecordEntityMapper.class));
    }

    static GenerationTaskApplicationService generationTaskService(
            AiAppEntityMapper aiAppEntityMapper,
            PromptTemplateEntityMapper promptTemplateEntityMapper,
            PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper,
            GenerationTaskEntityMapper generationTaskEntityMapper,
            GenerationRecordEntityMapper generationRecordEntityMapper) {
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);
        QuotaApplicationService quotaApplicationService = mock(QuotaApplicationService.class);
        GenerationTaskExecutionDispatcher generationTaskExecutionDispatcher = mock(GenerationTaskExecutionDispatcher.class);
        AiDirectGenerationApplicationService aiDirectGenerationApplicationService =
                mock(AiDirectGenerationApplicationService.class);
        AppVersionEntityMapper appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        GeneratedFileEntityMapper generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        lenient().when(appVersionEntityMapper.findByAppId(any())).thenReturn(List.of());
        lenient().when(generationTaskEntityMapper.findRunningTaskId(any())).thenReturn(null);
        lenient().when(generationTaskEntityMapper.selectOneById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return GenerationTaskEntity.builder()
                    .id(id)
                    .workspaceId(1001L)
                    .appId(3001L)
                    .taskType("APP_GENERATION")
                    .taskStatus("SUCCESS")
                    .queuedAt(LocalDateTime.of(2026, 7, 11, 10, 0))
                    .build();
        });
        return new GenerationTaskApplicationService(
                aiAppEntityMapper,
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                generationTaskEntityMapper,
                mock(GenerationTaskEventEntityMapper.class),
                generationRecordEntityMapper,
                workspaceAccessService,
                quotaApplicationService,
                new GenerationTaskStreamRegistry(new PublicGenerationStreamEventMapper(new ObjectMapper())),
                new PublicGenerationStreamEventMapper(new ObjectMapper()),
                generationTaskExecutionDispatcher,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                aiDirectGenerationApplicationService,
                new ObjectMapper(),
                noopTransactionManager());
    }

    private static PlatformTransactionManager noopTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
