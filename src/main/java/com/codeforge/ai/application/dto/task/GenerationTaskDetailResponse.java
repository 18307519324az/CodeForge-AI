package com.codeforge.ai.application.dto.task;

import java.time.LocalDateTime;

public record GenerationTaskDetailResponse(
        Long id,
        Long workspaceId,
        Long appId,
        String taskType,
        String taskStatus,
        String errorCode,
        String errorMessage,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
