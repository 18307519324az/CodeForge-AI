package com.codeforge.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateRequest;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateResponse;
import com.codeforge.ai.application.dto.task.GenerationTaskDetailResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateStatus;
import com.codeforge.ai.domain.task.entity.GenerationRecordEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationRecordEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GenerationTaskApplicationServiceTest {

    private AiAppEntityMapper aiAppEntityMapper;
    private PromptTemplateEntityMapper promptTemplateEntityMapper;
    private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private GenerationTaskEntityMapper generationTaskEntityMapper;
    private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    private GenerationRecordEntityMapper generationRecordEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private QuotaApplicationService quotaApplicationService;
    private GenerationTaskStreamRegistry generationTaskStreamRegistry;
    private GenerationTaskExecutionDispatcher generationTaskExecutionDispatcher;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private AiDirectGenerationApplicationService aiDirectGenerationApplicationService;
    private GenerationTaskApplicationService generationTaskApplicationService;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        promptTemplateEntityMapper = mock(PromptTemplateEntityMapper.class);
        promptTemplateVersionEntityMapper = mock(PromptTemplateVersionEntityMapper.class);
        generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        generationTaskEventEntityMapper = mock(GenerationTaskEventEntityMapper.class);
        generationRecordEntityMapper = mock(GenerationRecordEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        quotaApplicationService = mock(QuotaApplicationService.class);
        generationTaskStreamRegistry = new GenerationTaskStreamRegistry(
                new PublicGenerationStreamEventMapper(new ObjectMapper()));
        generationTaskExecutionDispatcher = mock(GenerationTaskExecutionDispatcher.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        aiDirectGenerationApplicationService = mock(AiDirectGenerationApplicationService.class);
        // default stubs for sync generation reload
        given(appVersionEntityMapper.findByAppId(any())).willReturn(java.util.Collections.emptyList());
        given(generationTaskEntityMapper.findRunningTaskId(any())).willReturn(null);
        given(generationTaskEntityMapper.selectOneById(any())).willAnswer(inv -> {
            Long id = inv.getArgument(0);
            return GenerationTaskEntity.builder().id(id).workspaceId(1001L).appId(3001L)
                    .taskType("RULE_GENERATION").taskStatus("SUCCESS").queuedAt(LocalDateTime.now()).build();
        });
        generationTaskApplicationService = new GenerationTaskApplicationService(
                aiAppEntityMapper,
                promptTemplateEntityMapper,
                promptTemplateVersionEntityMapper,
                generationTaskEntityMapper,
                generationTaskEventEntityMapper,
                generationRecordEntityMapper,
                workspaceAccessService,
                quotaApplicationService,
                generationTaskStreamRegistry,
                new PublicGenerationStreamEventMapper(new ObjectMapper()),
                generationTaskExecutionDispatcher,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                aiDirectGenerationApplicationService,
                new ObjectMapper(),
                noopTransactionManager()
        );
    }

    private PlatformTransactionManager noopTransactionManager() {
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

    @Test
    void shouldCreateQueuedTaskAndRecord() {
        given(generationTaskEntityMapper.findRunningTaskId(any())).willReturn(null);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .build());
        doAnswer(invocation -> {
            GenerationTaskEntity entity = invocation.getArgument(0);
            entity.setId(6001L);
            entity.setQueuedAt(LocalDateTime.of(2026, 6, 22, 17, 0));
            return 1;
        }).when(generationTaskEntityMapper).insertTask(any(GenerationTaskEntity.class));

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");
        request.setIdempotencyKey("idem_001");

        GenerationTaskCreateResponse response = generationTaskApplicationService.createTask(
                new CurrentUser(2001L, "editor", List.of("USER")),
                request
        );

        ArgumentCaptor<GenerationTaskEntity> taskCaptor = ArgumentCaptor.forClass(GenerationTaskEntity.class);
        ArgumentCaptor<GenerationRecordEntity> recordCaptor = ArgumentCaptor.forClass(GenerationRecordEntity.class);
        verify(generationTaskEntityMapper).insertTask(taskCaptor.capture());
        verify(generationRecordEntityMapper).insertRecord(recordCaptor.capture());
        verify(quotaApplicationService).recordTaskQuotaUsage(2001L, 1001L, 6001L);
        verify(aiAppEntityMapper).updateLatestTaskId(3001L, 6001L, 2001L);
        assertThat(taskCaptor.getValue().getIdempotencyKey()).isEqualTo("idem_001");
        assertThat(taskCaptor.getValue().getTaskStatus()).isEqualTo("QUEUED");
        assertThat(recordCaptor.getValue().getAppId()).isEqualTo(3001L);
        assertThat(recordCaptor.getValue().getTaskId()).isEqualTo(6001L);
        assertThat(recordCaptor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(response.taskId()).isEqualTo(6001L);
        assertThat(response.taskStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldCancelNonTerminalTask() {
        GenerationTaskEntity existing = GenerationTaskEntity.builder()
                .id(6002L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("QUEUED")
                .queuedAt(LocalDateTime.of(2026, 6, 22, 17, 10))
                .build();
        GenerationTaskEntity cancelled = GenerationTaskEntity.builder()
                .id(6002L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("CANCELLED")
                .queuedAt(existing.getQueuedAt())
                .finishedAt(LocalDateTime.of(2026, 6, 22, 17, 20))
                .build();
        given(generationTaskEntityMapper.selectOneById(6002L)).willReturn(existing, cancelled);
        given(generationTaskEntityMapper.cancelIfActive(any(), any(), any())).willReturn(1);

        GenerationTaskDetailResponse response = generationTaskApplicationService.cancelTask(
                new CurrentUser(2001L, "editor", List.of("USER")),
                6002L
        );

        verify(generationTaskEntityMapper).cancelIfActive(any(), any(), any());
        assertThat(response.taskStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldRejectPromptTemplateFromDifferentWorkspace() {
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
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(2002L)
                .status(PromptTemplateStatus.PUBLISHED.name())
                .build());

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(5001L);

        assertThatThrownBy(() -> generationTaskApplicationService.createTask(
                new CurrentUser(2001L, "editor", List.of("USER")), request))
                .hasMessage("promptTemplateId 与 workspaceId 不匹配");
    }

    @Test
    void shouldRejectUnpublishedPromptTemplateForProductionGeneration() {
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
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.DRAFT.name())
                .currentVersionNo(1)
                .build());

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(5001L);

        assertThatThrownBy(() -> generationTaskApplicationService.createTask(
                new CurrentUser(2001L, "editor", List.of("USER")), request))
                .hasMessage("Prompt 模板未发布，无法用于生产生成");
    }

    @Test
    void shouldRejectDraftPromptTemplateVersionForProductionGeneration() {
        given(generationTaskEntityMapper.findRunningTaskId(any())).willReturn(null);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .build());
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L)
                .workspaceId(1001L)
                .status(PromptTemplateStatus.PUBLISHED.name())
                .currentVersionNo(2)
                .build());
        given(promptTemplateVersionEntityMapper.selectOneById(5002L)).willReturn(
                PromptTemplateVersionEntity.builder()
                        .id(5002L)
                        .templateId(4001L)
                        .versionNo(2)
                        .publishedAt(null)
                        .build()
        );

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");
        request.setPromptTemplateId(4001L);
        request.setPromptTemplateVersionId(5002L);

        assertThatThrownBy(() -> generationTaskApplicationService.createTask(
                new CurrentUser(2001L, "editor", List.of("USER")), request))
                .hasMessage("仅已发布版本可用于生产生成");
    }

    @Test
    void shouldReuseExistingTaskForIdempotencyKey() {
        GenerationTaskEntity existingTask = GenerationTaskEntity.builder()
                .id(7001L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("QUEUED")
                .idempotencyKey("idem_001")
                .requestPayloadJson("{\"workspaceId\":1001,\"appId\":3001,\"taskType\":\"APP_GENERATION\",\"promptTemplateId\":null,\"promptTemplateVersionNo\":null,\"promptTemplateVersionId\":null,\"templateVariables\":null,\"requirement\":\"build dashboard\",\"idempotencyKey\":\"idem_001\"}")
                .requestId("req_existing")
                .queuedAt(LocalDateTime.of(2026, 6, 22, 18, 0))
                .build();
        given(generationTaskEntityMapper.findRunningTaskId(any())).willReturn(null);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .build());
        given(generationTaskEntityMapper.findByIdempotencyKey(1001L, 3001L, "idem_001")).willReturn(existingTask);

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");
        request.setIdempotencyKey("idem_001");

        GenerationTaskCreateResponse response = generationTaskApplicationService.createTask(
                new CurrentUser(2001L, "editor", List.of("USER")),
                request
        );

        verify(generationTaskEntityMapper, never()).insertTask(any(GenerationTaskEntity.class));
        assertThat(response.taskId()).isEqualTo(7001L);
    }

    @Test
    void shouldNormalizeIdempotencyKeyBeforeReusingTask() {
        GenerationTaskEntity existingTask = GenerationTaskEntity.builder()
                .id(7002L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("QUEUED")
                .idempotencyKey("idem_001")
                .requestPayloadJson("{\"workspaceId\":1001,\"appId\":3001,\"taskType\":\"APP_GENERATION\",\"promptTemplateId\":null,\"promptTemplateVersionNo\":null,\"promptTemplateVersionId\":null,\"templateVariables\":null,\"requirement\":\"build dashboard\",\"idempotencyKey\":\"idem_001\"}")
                .requestId("req_existing")
                .queuedAt(LocalDateTime.of(2026, 6, 22, 18, 5))
                .build();
        given(generationTaskEntityMapper.findRunningTaskId(any())).willReturn(null);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .build());
        given(generationTaskEntityMapper.findByIdempotencyKey(1001L, 3001L, "idem_001")).willReturn(existingTask);

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");
        request.setIdempotencyKey(" idem_001 ");

        GenerationTaskCreateResponse response = generationTaskApplicationService.createTask(
                new CurrentUser(2001L, "editor", List.of("USER")),
                request
        );

        verify(generationTaskEntityMapper, never()).insertTask(any(GenerationTaskEntity.class));
        assertThat(response.taskId()).isEqualTo(7002L);
    }

    @Test
    void shouldAllowSameIdempotencyKeyAcrossDifferentWorkspaces() {
        given(aiAppEntityMapper.selectOneById(4001L)).willReturn(AiAppEntity.builder()
                .id(4001L)
                .workspaceId(2002L)
                .name("App B")
                .build());
        doAnswer(invocation -> {
            GenerationTaskEntity entity = invocation.getArgument(0);
            entity.setId(7003L);
            entity.setQueuedAt(LocalDateTime.of(2026, 6, 22, 18, 6));
            return 1;
        }).when(generationTaskEntityMapper).insertTask(any(GenerationTaskEntity.class));

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(2002L);
        request.setAppId(4001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build portal");
        request.setIdempotencyKey("idem_001");

        GenerationTaskCreateResponse response = generationTaskApplicationService.createTask(
                new CurrentUser(2002L, "editor_b", List.of("USER")),
                request
        );

        verify(generationTaskEntityMapper, never()).findByIdempotencyKey(1001L, 3001L, "idem_001");
        verify(generationTaskEntityMapper).findByIdempotencyKey(2002L, 4001L, "idem_001");
        assertThat(response.taskId()).isEqualTo(7003L);
    }

    @Test
    void shouldKeepSourceRecordWhenRetryingTask() {
        GenerationTaskEntity sourceTask = GenerationTaskEntity.builder()
                .id(6003L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("FAILED")
                .retryCount(0)
                .requestPayloadJson("{\"requirement\":\"build dashboard\"}")
                .build();
        given(generationTaskEntityMapper.selectOneById(6003L)).willReturn(sourceTask);
        given(generationRecordEntityMapper.findLatestByTaskId(6003L)).willReturn(GenerationRecordEntity.builder()
                .id(8001L)
                .taskId(6003L)
                .promptTemplateVersionId(5001L)
                .inputSummary("build dashboard")
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
            entity.setQueuedAt(LocalDateTime.of(2026, 6, 22, 18, 10));
            return 1;
        }).when(generationTaskEntityMapper).insertTask(any(GenerationTaskEntity.class));

        GenerationTaskCreateResponse response = generationTaskApplicationService.retryTask(
                new CurrentUser(2001L, "editor", List.of("USER")),
                6003L
        );

        ArgumentCaptor<GenerationRecordEntity> recordCaptor = ArgumentCaptor.forClass(GenerationRecordEntity.class);
        verify(generationRecordEntityMapper).insertRecord(recordCaptor.capture());
        verify(quotaApplicationService).recordTaskQuotaUsage(2001L, 1001L, 6004L);
        verify(generationTaskExecutionDispatcher).scheduleTaskExecution(6004L, 2001L, response.requestId());
        assertThat(recordCaptor.getValue().getPromptTemplateVersionId()).isEqualTo(5001L);
        assertThat(recordCaptor.getValue().getInputSummary()).isEqualTo("build dashboard");
        assertThat(response.taskId()).isEqualTo(6004L);
    }

    @Test
    void shouldBlockTaskCreationWhenQuotaNotEnough() {
        given(generationTaskEntityMapper.findRunningTaskId(any())).willReturn(null);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .build());
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.QUOTA_NOT_ENOUGH))
                .when(quotaApplicationService).assertQuotaAvailable(2001L, 1001L);

        GenerationTaskCreateRequest request = new GenerationTaskCreateRequest();
        request.setWorkspaceId(1001L);
        request.setAppId(3001L);
        request.setTaskType("APP_GENERATION");
        request.setRequirement("build dashboard");

        assertThatThrownBy(() -> generationTaskApplicationService.createTask(
                new CurrentUser(2001L, "editor", List.of("USER")),
                request
        )).isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.QUOTA_NOT_ENOUGH.getMessage());

        verify(generationTaskEntityMapper, never()).insertTask(any(GenerationTaskEntity.class));
    }

    @Test
    void shouldOpenPersistentTaskStreamForNonTerminalTask() {
        GenerationTaskEntity queuedTask = GenerationTaskEntity.builder()
                .id(9001L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("QUEUED")
                .build();
        given(generationTaskEntityMapper.selectOneById(9001L)).willReturn(queuedTask, queuedTask);
        given(generationTaskEventEntityMapper.findByTaskId(9001L)).willReturn(List.of());

        SseEmitter emitter = generationTaskApplicationService.openTaskStream(
                new CurrentUser(2001L, "editor", List.of("USER")),
                9001L,
                null
        );

        assertThat(emitter).isNotNull();
        assertThat(generationTaskStreamRegistry.subscriberCount(9001L)).isEqualTo(1);
    }

    @Test
    void shouldImmediatelyEndStreamForTerminalTaskAndReturnTerminalDetail() {
        GenerationTaskEntity successTask = GenerationTaskEntity.builder()
                .id(9003L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("SUCCESS")
                .finishedAt(LocalDateTime.of(2026, 6, 22, 19, 0))
                .build();
        GenerationTaskEventEntity successEvent = GenerationTaskEventEntity.builder()
                .id(1L)
                .taskId(9003L)
                .eventType("TASK_SUCCESS")
                .eventMessage("任务执行完成")
                .eventPayloadJson("{\"taskStatus\":\"SUCCESS\"}")
                .requestId("req_success")
                .build();
        successEvent.setCreatedAt(LocalDateTime.of(2026, 6, 22, 19, 0));
        given(generationTaskEntityMapper.selectOneById(9003L)).willReturn(successTask, successTask, successTask);
        given(generationTaskEventEntityMapper.findByTaskId(9003L)).willReturn(List.of(successEvent));

        SseEmitter emitter = generationTaskApplicationService.openTaskStream(
                new CurrentUser(2001L, "editor", List.of("USER")),
                9003L,
                null
        );
        GenerationTaskDetailResponse detailResponse = generationTaskApplicationService.getTask(
                new CurrentUser(2001L, "editor", List.of("USER")),
                9003L
        );

        assertThat(emitter).isNotNull();
        assertThat(generationTaskStreamRegistry.subscriberCount(9003L)).isEqualTo(0);
        assertThat(detailResponse.taskStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldRejectCancelWhenConcurrentStateAlreadyChanged() {
        GenerationTaskEntity existing = GenerationTaskEntity.builder()
                .id(6005L)
                .workspaceId(1001L)
                .appId(3001L)
                .taskType("APP_GENERATION")
                .taskStatus("QUEUED")
                .build();
        given(generationTaskEntityMapper.selectOneById(6005L)).willReturn(existing);
        given(generationTaskEntityMapper.cancelIfActive(any(), any(), any())).willReturn(0);

        assertThatThrownBy(() -> generationTaskApplicationService.cancelTask(
                new CurrentUser(2001L, "editor", List.of("USER")),
                6005L
        )).hasMessage("任务当前状态不允许取消");
    }
}
