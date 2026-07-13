package com.codeforge.ai.application.dto.prompt;

import java.time.LocalDateTime;

public record PromptTemplateUserListItemResponse(
        Long id,
        String templateName,
        String description,
        String templateScene,
        String templateSceneLabel,
        String applicableAppType,
        Integer currentVersionNo,
        Long publishedVersionId,
        LocalDateTime updatedAt
) {
}
