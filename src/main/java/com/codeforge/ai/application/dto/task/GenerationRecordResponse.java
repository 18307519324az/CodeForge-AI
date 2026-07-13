package com.codeforge.ai.application.dto.task;

import java.time.LocalDateTime;

public record GenerationRecordResponse(
        Long id,
        Long taskId,
        Long promptTemplateVersionId,
        Long modelProviderId,
        String modelName,
        String inputSummary,
        String outputSummary,
        Integer tokenInput,
        Integer tokenOutput,
        Long durationMs,
        LocalDateTime createdAt
) {
}
