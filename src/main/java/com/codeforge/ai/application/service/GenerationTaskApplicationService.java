package com.codeforge.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.codeforge.ai.application.dto.task.GenerationRecordResponse;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateRequest;
import com.codeforge.ai.application.dto.task.GenerationTaskCreateResponse;
import com.codeforge.ai.application.dto.task.GenerationTaskDetailResponse;
import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.common.BaseEntity;
import com.codeforge.ai.domain.prompt.enums.PromptTemplateStatus;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.model.PromptTemplateRenderer;
import com.codeforge.ai.domain.task.entity.GenerationRecordEntity;
import com.codeforge.ai.domain.generation.model.ProviderErrorSanitizer;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
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
import com.codeforge.ai.shared.response.ResultUtils;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class GenerationTaskApplicationService {

    private final AiAppEntityMapper aiAppEntityMapper;
    private final PromptTemplateEntityMapper promptTemplateEntityMapper;
    private final PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    private final GenerationTaskEntityMapper generationTaskEntityMapper;
    private final GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    private final GenerationRecordEntityMapper generationRecordEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final QuotaApplicationService quotaApplicationService;
    private final GenerationTaskStreamRegistry generationTaskStreamRegistry;
    private final PublicGenerationStreamEventMapper publicGenerationStreamEventMapper;
    private final GenerationTaskExecutionDispatcher generationTaskExecutionDispatcher;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final AiDirectGenerationApplicationService aiDirectGenerationApplicationService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public GenerationTaskCreateResponse createTask(CurrentUser currentUser, GenerationTaskCreateRequest request) {
        EnqueueResult enqueued = new TransactionTemplate(transactionManager)
                .execute(status -> enqueueTask(currentUser, request));
        if (enqueued == null) {
            throw new IllegalStateException("任务入队失败");
        }
        if (enqueued.reuseResponse() != null) {
            return enqueued.reuseResponse();
        }
        executeSyncGeneration(
                enqueued.task(),
                enqueued.app(),
                enqueued.requirement(),
                enqueued.userId(),
                enqueued.requestId());
        GenerationTaskEntity refreshed = generationTaskEntityMapper.selectOneById(enqueued.task().getId());
        return toCreateResponse(refreshed);
    }

    private EnqueueResult enqueueTask(CurrentUser currentUser, GenerationTaskCreateRequest request) {
        AiAppEntity appEntity = requireAppForTask(currentUser, request.getWorkspaceId(), request.getAppId());
        quotaApplicationService.assertQuotaAvailable(currentUser.requiredUserId(), request.getWorkspaceId());
        PromptTemplateVersionEntity promptTemplateVersion = resolvePromptTemplateVersion(
                currentUser, request.getWorkspaceId(), request);
        if (promptTemplateVersion != null) {
            PromptTemplateRenderer.validateRequiredVariables(
                    promptTemplateVersion.getSystemPrompt(),
                    promptTemplateVersion.getUserPrompt(),
                    request.getTemplateVariables());
        }
        String requestId = ResultUtils.currentRequestId();
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        String requestPayloadJson = buildRequestPayloadJson(request, idempotencyKey);
        if (idempotencyKey != null) {
            GenerationTaskEntity existingTask = generationTaskEntityMapper.findByIdempotencyKey(
                    request.getWorkspaceId(), request.getAppId(), idempotencyKey);
            if (existingTask != null) {
                return EnqueueResult.reuse(reuseExistingTaskForIdempotency(currentUser, existingTask, requestPayloadJson));
            }
        }
        GenerationTaskEntity taskEntity = GenerationTaskEntity.builder()
                .workspaceId(request.getWorkspaceId())
                .appId(request.getAppId())
                .taskType(request.getTaskType())
                .taskStatus(GenerationTaskStatus.QUEUED.name())
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .requestPayloadJson(requestPayloadJson)
                .promptTemplateId(promptTemplateVersion == null ? null : request.getPromptTemplateId())
                .promptTemplateVersionId(promptTemplateVersion == null ? null : promptTemplateVersion.getId())
                .requestId(requestId)
                .queuedAt(LocalDateTime.now())
                .build();
        taskEntity.setCreatedBy(currentUser.requiredUserId());
        taskEntity.setUpdatedBy(currentUser.requiredUserId());
        applyAuditFields(taskEntity, currentUser.requiredUserId());
        try {
            generationTaskEntityMapper.insertTask(taskEntity);
        } catch (DuplicateKeyException exception) {
            if (idempotencyKey == null) {
                throw exception;
            }
            GenerationTaskEntity existingTask = generationTaskEntityMapper.findByIdempotencyKey(
                    request.getWorkspaceId(), request.getAppId(), idempotencyKey);
            if (existingTask != null) {
                return EnqueueResult.reuse(reuseExistingTaskForIdempotency(currentUser, existingTask, requestPayloadJson));
            }
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "idempotencyKey 已被其他请求占用");
        }

        saveEvent(taskEntity.getId(), GenerationTaskEventType.TASK_CREATED,
                "任务已创建并进入队列", "{\"taskStatus\":\"QUEUED\"}", requestId, currentUser.requiredUserId());
        saveRecord(appEntity, taskEntity,
                promptTemplateVersion == null ? null : promptTemplateVersion.getId(),
                request.getRequirement(), currentUser.requiredUserId());
        quotaApplicationService.recordTaskQuotaUsage(currentUser.requiredUserId(), request.getWorkspaceId(), taskEntity.getId());
        aiAppEntityMapper.updateLatestTaskId(appEntity.getId(), taskEntity.getId(), currentUser.requiredUserId());
        return EnqueueResult.queued(
                taskEntity,
                appEntity,
                request.getRequirement(),
                currentUser.requiredUserId(),
                requestId);
    }

    public void executeSyncGeneration(GenerationTaskEntity task, AiAppEntity app, String requirement, Long userId, String requestId) {
        aiDirectGenerationApplicationService.executeSync(task, app, requirement, userId, requestId);
    }

    private AiAppEntity requireAppForTask(CurrentUser currentUser, Long workspaceId, Long appId) {
        workspaceAccessService.requireEditorAccess(currentUser, workspaceId);
        AiAppEntity appEntity = requireAppReadable(currentUser, appId);
        if (!workspaceId.equals(appEntity.getWorkspaceId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "workspaceId 与 appId 不匹配");
        }
        return appEntity;
    }

    public GenerationTaskDetailResponse getTask(CurrentUser currentUser, Long taskId) {
        GenerationTaskEntity taskEntity = requireTaskReadable(currentUser, taskId);
        return toDetailResponse(taskEntity);
    }

    public List<PublicGenerationStreamEvent> listTaskEvents(CurrentUser currentUser, Long taskId, Long afterEventId) {
        GenerationTaskEntity taskEntity = requireTaskReadable(currentUser, taskId);
        return listPublicTaskEventsByTaskId(taskEntity.getId(), afterEventId);
    }

    public SseEmitter openTaskStream(CurrentUser currentUser, Long taskId, Long afterEventId) {
        requireTaskReadable(currentUser, taskId);
        return generationTaskStreamRegistry.subscribe(
                taskId,
                afterEventId,
                () -> listPublicTaskEventsByTaskId(taskId, null),
                lastEventId -> listPublicTaskEventsByTaskId(taskId, lastEventId),
                () -> {
                    GenerationTaskEntity latestTask = generationTaskEntityMapper.selectOneById(taskId);
                    return latestTask == null || isTerminal(latestTask.getTaskStatus());
                });
    }

    public List<GenerationRecordResponse> listGenerationRecords(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = requireAppReadable(currentUser, appId);
        return generationRecordEntityMapper.findByAppId(appEntity.getId()).stream()
                .map(this::toRecordResponse)
                .toList();
    }

    @Transactional
    public GenerationTaskDetailResponse cancelTask(CurrentUser currentUser, Long taskId) {
        GenerationTaskEntity taskEntity = requireTaskEditable(currentUser, taskId);
        if (isTerminal(taskEntity.getTaskStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "任务当前状态不允许取消");
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        int updatedRows = generationTaskEntityMapper.cancelIfActive(taskEntity.getId(), finishedAt, currentUser.requiredUserId());
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "任务当前状态不允许取消");
        }
        saveEvent(taskEntity.getId(), GenerationTaskEventType.TASK_CANCELLED,
                "任务已取消", "{\"taskStatus\":\"CANCELLED\"}", ResultUtils.currentRequestId(), currentUser.requiredUserId());
        GenerationTaskEntity refreshed = generationTaskEntityMapper.selectOneById(taskId);
        return toDetailResponse(refreshed);
    }

    @Transactional
    public GenerationTaskCreateResponse retryTask(CurrentUser currentUser, Long taskId) {
        GenerationTaskEntity sourceTask = requireTaskEditable(currentUser, taskId);
        if (!isRetryable(sourceTask.getTaskStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "任务当前状态不允许重试");
        }
        quotaApplicationService.assertQuotaAvailable(currentUser.requiredUserId(), sourceTask.getWorkspaceId());
        String retryRequestId = ResultUtils.currentRequestId();
        GenerationRecordEntity sourceRecord = generationRecordEntityMapper.findLatestByTaskId(sourceTask.getId());
        Long retryTemplateId = sourceTask.getPromptTemplateId();
        Long retryTemplateVersionId = sourceTask.getPromptTemplateVersionId();
        if (retryTemplateId == null && retryTemplateVersionId == null) {
            com.codeforge.ai.application.task.GenerationTaskRequestPayloadSupport.ParsedPayload legacyPayload =
                    com.codeforge.ai.application.task.GenerationTaskRequestPayloadSupport.parse(
                            objectMapper, sourceTask.getRequestPayloadJson());
            retryTemplateId = legacyPayload.promptTemplateId();
            retryTemplateVersionId = legacyPayload.promptTemplateVersionId();
        }
        if ((retryTemplateId != null && retryTemplateVersionId == null)
                || (retryTemplateId == null && retryTemplateVersionId != null)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "历史任务缺少完整模板版本信息，无法安全重试");
        }
        GenerationTaskEntity retryTask = GenerationTaskEntity.builder()
                .workspaceId(sourceTask.getWorkspaceId())
                .appId(sourceTask.getAppId())
                .taskType(sourceTask.getTaskType())
                .taskStatus(GenerationTaskStatus.QUEUED.name())
                .retryOfTaskId(sourceTask.getId())
                .retryCount(sourceTask.getRetryCount() == null ? 1 : sourceTask.getRetryCount() + 1)
                .requestPayloadJson(sourceTask.getRequestPayloadJson())
                .promptTemplateId(retryTemplateId)
                .promptTemplateVersionId(retryTemplateVersionId)
                .requestId(retryRequestId)
                .queuedAt(LocalDateTime.now())
                .build();
        retryTask.setCreatedBy(currentUser.requiredUserId());
        retryTask.setUpdatedBy(currentUser.requiredUserId());
        applyAuditFields(retryTask, currentUser.requiredUserId());
        generationTaskEntityMapper.insertTask(retryTask);

        saveEvent(retryTask.getId(), GenerationTaskEventType.TASK_CREATED,
                "任务由重试创建并重新入队", "{\"sourceTaskId\":" + sourceTask.getId() + "}",
                retryRequestId, currentUser.requiredUserId());
        saveRecord(requireAppReadable(currentUser, sourceTask.getAppId()), retryTask,
                retryTemplateVersionId != null ? retryTemplateVersionId
                        : (sourceRecord == null ? null : sourceRecord.getPromptTemplateVersionId()),
                sourceRecord == null ? extractRequirementFromPayload(sourceTask.getRequestPayloadJson()) : sourceRecord.getInputSummary(),
                currentUser.requiredUserId());
        quotaApplicationService.recordTaskQuotaUsage(currentUser.requiredUserId(), sourceTask.getWorkspaceId(), retryTask.getId());
        aiAppEntityMapper.updateLatestTaskId(sourceTask.getAppId(), retryTask.getId(), currentUser.requiredUserId());
        generationTaskExecutionDispatcher.scheduleTaskExecution(retryTask.getId(), currentUser.requiredUserId(), retryRequestId);
        return toCreateResponse(retryTask);
    }

    private AiAppEntity requireAppReadable(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireReadAccess(currentUser, appEntity.getWorkspaceId());
        return appEntity;
    }

    private PromptTemplateVersionEntity resolvePromptTemplateVersion(CurrentUser currentUser,
                                                                    Long workspaceId,
                                                                    GenerationTaskCreateRequest request) {
        if (request.getPromptTemplateId() == null && request.getPromptTemplateVersionId() == null) {
            return null;
        }
        if (request.getPromptTemplateId() == null || request.getPromptTemplateVersionId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "promptTemplateId 与 promptTemplateVersionId 必须同时提供");
        }
        PromptTemplateVersionEntity versionEntity = promptTemplateVersionEntityMapper.selectOneById(
                request.getPromptTemplateVersionId());
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "Prompt 模板版本不存在");
        }
        if (!request.getPromptTemplateId().equals(versionEntity.getTemplateId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "promptTemplateVersionId 与 promptTemplateId 不匹配");
        }
        PromptTemplateEntity templateEntity = promptTemplateEntityMapper.selectOneById(versionEntity.getTemplateId());
        if (templateEntity == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        if (!workspaceId.equals(templateEntity.getWorkspaceId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "promptTemplateId 与 workspaceId 不匹配");
        }
        if (!PromptTemplateStatus.PUBLISHED.name().equals(templateEntity.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Prompt 模板未发布，无法用于生产生成");
        }
        if (!versionEntity.isEffectivelyPublished()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅已发布版本可用于生产生成");
        }
        workspaceAccessService.requireReadAccess(currentUser, templateEntity.getWorkspaceId());
        return versionEntity;
    }

    private GenerationTaskEntity requireTaskReadable(CurrentUser currentUser, Long taskId) {
        GenerationTaskEntity taskEntity = generationTaskEntityMapper.selectOneById(taskId);
        if (taskEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        workspaceAccessService.requireReadAccess(currentUser, taskEntity.getWorkspaceId());
        return taskEntity;
    }

    private GenerationTaskEntity requireTaskEditable(CurrentUser currentUser, Long taskId) {
        GenerationTaskEntity taskEntity = generationTaskEntityMapper.selectOneById(taskId);
        if (taskEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        workspaceAccessService.requireEditorAccess(currentUser, taskEntity.getWorkspaceId());
        return taskEntity;
    }

    private void saveEvent(Long taskId,
                           GenerationTaskEventType eventType,
                           String message,
                           String payloadJson,
                           String requestId,
                           Long userId) {
        GenerationTaskEventEntity eventEntity = GenerationTaskEventEntity.builder()
                .taskId(taskId)
                .eventType(eventType.name())
                .eventMessage(message)
                .eventPayloadJson(payloadJson)
                .requestId(requestId)
                .build();
        applyAuditFields(eventEntity, userId);
        generationTaskEventEntityMapper.insertEvent(eventEntity);
        PublicGenerationStreamEvent publicEvent = publicGenerationStreamEventMapper.fromEntity(eventEntity);
        generationTaskStreamRegistry.publish(taskId, publicEvent, isTerminalEvent(eventType));
    }

    private void saveRecord(AiAppEntity appEntity, GenerationTaskEntity taskEntity,
                            Long promptTemplateVersionId, String requirement, Long userId) {
        GenerationRecordEntity recordEntity = GenerationRecordEntity.builder()
                .workspaceId(appEntity.getWorkspaceId())
                .appId(appEntity.getId())
                .taskId(taskEntity.getId())
                .status(GenerationTaskStatus.QUEUED.name())
                .promptTemplateVersionId(promptTemplateVersionId)
                .inputSummary(truncateSummary(requirement))
                .tokenInput(0)
                .tokenOutput(0)
                .durationMs(0L)
                .build();
        applyAuditFields(recordEntity, userId);
        generationRecordEntityMapper.insertRecord(recordEntity);
    }

    private void applyAuditFields(BaseEntity entity, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        if (entity.getIsDeleted() == null) {
            entity.setIsDeleted(0);
        }
    }

    private boolean isTerminal(String taskStatus) {
        return GenerationTaskStatus.SUCCESS.name().equals(taskStatus)
                || GenerationTaskStatus.FAILED.name().equals(taskStatus)
                || GenerationTaskStatus.CANCELLED.name().equals(taskStatus);
    }

    private boolean isRetryable(String taskStatus) {
        return GenerationTaskStatus.FAILED.name().equals(taskStatus)
                || GenerationTaskStatus.CANCELLED.name().equals(taskStatus);
    }

    private String buildRequestPayloadJson(GenerationTaskCreateRequest request, String normalizedIdempotencyKey) {
        ObjectNode payloadNode = objectMapper.createObjectNode();
        payloadNode.put("workspaceId", request.getWorkspaceId());
        payloadNode.put("appId", request.getAppId());
        payloadNode.put("taskType", request.getTaskType());
        if (request.getPromptTemplateId() == null) {
            payloadNode.putNull("promptTemplateId");
        } else {
            payloadNode.put("promptTemplateId", request.getPromptTemplateId());
        }
        if (request.getPromptTemplateVersionNo() == null) {
            payloadNode.putNull("promptTemplateVersionNo");
        } else {
            payloadNode.put("promptTemplateVersionNo", request.getPromptTemplateVersionNo());
        }
        if (request.getPromptTemplateVersionId() == null) {
            payloadNode.putNull("promptTemplateVersionId");
        } else {
            payloadNode.put("promptTemplateVersionId", request.getPromptTemplateVersionId());
        }
        if (request.getTemplateVariables() == null || request.getTemplateVariables().isEmpty()) {
            payloadNode.putNull("templateVariables");
        } else {
            payloadNode.set("templateVariables", objectMapper.valueToTree(request.getTemplateVariables()));
        }
        payloadNode.put("requirement", request.getRequirement());
        if (normalizedIdempotencyKey == null) {
            payloadNode.putNull("idempotencyKey");
        } else {
            payloadNode.put("idempotencyKey", normalizedIdempotencyKey);
        }
        try {
            return objectMapper.writeValueAsString(payloadNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("任务请求载荷序列化失败", exception);
        }
    }

    private String truncateSummary(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private record EnqueueResult(
            GenerationTaskCreateResponse reuseResponse,
            GenerationTaskEntity task,
            AiAppEntity app,
            String requirement,
            Long userId,
            String requestId
    ) {
        static EnqueueResult reuse(GenerationTaskCreateResponse response) {
            return new EnqueueResult(response, null, null, null, null, null);
        }

        static EnqueueResult queued(GenerationTaskEntity task,
                                    AiAppEntity app,
                                    String requirement,
                                    Long userId,
                                    String requestId) {
            return new EnqueueResult(null, task, app, requirement, userId, requestId);
        }
    }

    private GenerationTaskCreateResponse reuseExistingTaskForIdempotency(CurrentUser currentUser,
                                                                         GenerationTaskEntity existingTask,
                                                                         String requestPayloadJson) {
        workspaceAccessService.requireReadAccess(currentUser, existingTask.getWorkspaceId());
        if (!requestPayloadJson.equals(existingTask.getRequestPayloadJson())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "idempotencyKey 已被其他请求占用");
        }
        return toCreateResponse(existingTask);
    }

    private String extractRequirementFromPayload(String requestPayloadJson) {
        if (requestPayloadJson == null || requestPayloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode payloadNode = objectMapper.readTree(requestPayloadJson);
            JsonNode requirementNode = payloadNode.get("requirement");
            return requirementNode == null || requirementNode.isNull() ? null : truncateSummary(requirementNode.asText());
        } catch (JsonProcessingException exception) {
            return truncateSummary(requestPayloadJson);
        }
    }

    private boolean isTerminalEvent(GenerationTaskEventType eventType) {
        return GenerationTaskEventType.TASK_SUCCESS.equals(eventType)
                || GenerationTaskEventType.TASK_FAILED.equals(eventType)
                || GenerationTaskEventType.TASK_CANCELLED.equals(eventType);
    }

    private List<PublicGenerationStreamEvent> listPublicTaskEventsByTaskId(Long taskId, Long afterEventId) {
        List<GenerationTaskEventEntity> entities = afterEventId == null || afterEventId <= 0
                ? generationTaskEventEntityMapper.findByTaskId(taskId)
                : generationTaskEventEntityMapper.findByTaskIdAfterId(taskId, afterEventId);
        return entities.stream()
                .map(publicGenerationStreamEventMapper::fromEntity)
                .toList();
    }

    private GenerationTaskCreateResponse toCreateResponse(GenerationTaskEntity entity) {
        return new GenerationTaskCreateResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getAppId(),
                entity.getTaskType(),
                entity.getTaskStatus(),
                entity.getRequestId(),
                entity.getQueuedAt()
        );
    }

    private GenerationTaskDetailResponse toDetailResponse(GenerationTaskEntity entity) {
        ProviderErrorSanitizer.PublicTaskError publicError = ProviderErrorSanitizer.sanitizeStoredTaskError(
                entity.getErrorCode(),
                entity.getErrorMessage());
        return new GenerationTaskDetailResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getAppId(),
                entity.getTaskType(),
                entity.getTaskStatus(),
                publicError.errorCode(),
                publicError.errorMessage(),
                entity.getQueuedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    private GenerationRecordResponse toRecordResponse(GenerationRecordEntity entity) {
        return new GenerationRecordResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getPromptTemplateVersionId(),
                entity.getModelProviderId(),
                entity.getModelName(),
                entity.getInputSummary(),
                entity.getOutputSummary(),
                entity.getTokenInput(),
                entity.getTokenOutput(),
                entity.getDurationMs(),
                entity.getCreatedAt()
        );
    }
}
