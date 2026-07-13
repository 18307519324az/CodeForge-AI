package com.codeforge.ai.application.dto.task;

import java.time.LocalDateTime;

public record GenerationTaskEventResponse(
        Long id,
        Long taskId,
        String eventType,
        String eventMessage,
        String eventPayloadJson,
        String requestId,
        LocalDateTime timestamp,
        LocalDateTime createdAt
) {
}
