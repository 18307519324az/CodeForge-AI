package com.codeforge.ai.application.dto.task;

import java.time.LocalDateTime;

public record GenerationTaskCreateResponse(
        Long taskId,
        Long workspaceId,
        Long appId,
        String taskType,
        String taskStatus,
        String requestId,
        LocalDateTime queuedAt
) {
}
