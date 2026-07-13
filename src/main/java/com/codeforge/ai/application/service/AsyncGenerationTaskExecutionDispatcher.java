package com.codeforge.ai.application.service;

import com.codeforge.ai.application.task.GenerationTaskRequestPayloadSupport;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncGenerationTaskExecutionDispatcher implements GenerationTaskExecutionDispatcher {

    private final GenerationTaskEntityMapper generationTaskEntityMapper;
    private final AiAppEntityMapper aiAppEntityMapper;
    private final AiDirectGenerationApplicationService aiDirectGenerationApplicationService;
    private final ObjectMapper objectMapper;
    @Qualifier("generationTaskExecutor")
    private final Executor generationTaskExecutor;

    @Override
    public void scheduleTaskExecution(Long taskId, Long userId, String requestId) {
        Runnable execution = () -> executeTaskLifecycleSafely(taskId, userId, requestId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    generationTaskExecutor.execute(execution);
                }
            });
            return;
        }
        generationTaskExecutor.execute(execution);
    }

    private void executeTaskLifecycleSafely(Long taskId, Long userId, String requestId) {
        try {
            executeTaskLifecycle(taskId, userId, requestId);
        } catch (RuntimeException exception) {
            log.error("Generation task execution failed, taskId={}, requestId={}", taskId, requestId, exception);
        }
    }

    void executeTaskLifecycle(Long taskId, Long userId, String requestId) {
        GenerationTaskEntity taskEntity = generationTaskEntityMapper.selectOneById(taskId);
        if (taskEntity == null || isTerminalStatus(taskEntity.getTaskStatus())) {
            return;
        }
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(taskEntity.getAppId());
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        String requirement = GenerationTaskRequestPayloadSupport.parse(objectMapper, taskEntity.getRequestPayloadJson())
                .requirement();
        if (requirement == null || requirement.isBlank()) {
            requirement = "No requirement provided";
        }
        aiDirectGenerationApplicationService.executeSync(taskEntity, appEntity, requirement, userId, requestId);
    }

    private boolean isTerminalStatus(String taskStatus) {
        return GenerationTaskStatus.SUCCESS.name().equals(taskStatus)
                || GenerationTaskStatus.FAILED.name().equals(taskStatus)
                || GenerationTaskStatus.CANCELLED.name().equals(taskStatus);
    }
}
