package com.codeforge.ai.application.dto.task;

import java.time.LocalDateTime;
import java.util.Map;

public record PublicGenerationStreamEvent(
        String eventId,
        String taskId,
        String type,
        String stage,
        String message,
        LocalDateTime timestamp,
        boolean terminal,
        Map<String, Object> data
) {
}
