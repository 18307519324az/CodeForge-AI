package com.codeforge.ai.application.dto.prompt;

import java.time.LocalDateTime;

public record PromptTemplatePublishedVersionResponse(
        Long id,
        Integer versionNo,
        LocalDateTime publishedAt
) {
}
