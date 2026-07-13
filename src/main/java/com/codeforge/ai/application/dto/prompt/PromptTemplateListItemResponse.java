package com.codeforge.ai.application.dto.prompt;

import java.time.LocalDateTime;

public record PromptTemplateListItemResponse(
        Long id,
        Long workspaceId,
        String templateName,
        String templateScene,
        String status,
        Integer currentVersionNo,
        Integer versionCount,
        LocalDateTime updatedAt
) {
}
