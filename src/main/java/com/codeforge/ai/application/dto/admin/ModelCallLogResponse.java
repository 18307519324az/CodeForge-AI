package com.codeforge.ai.application.dto.admin;

import java.time.LocalDateTime;

public record ModelCallLogResponse(
        Long id,
        Long taskId,
        Long appId,
        Long providerId,
        String providerCode,
        String modelName,
        String requestId,
        String status,
        Integer inputTokens,
        Integer outputTokens,
        Long durationMs,
        Boolean fallbackUsed,
        String generationSource,
        Long promptTemplateVersionId,
        String promptTemplateCode,
        Integer promptTemplateVersionNo,
        String errorMessage,
        LocalDateTime createdAt
) {
}
