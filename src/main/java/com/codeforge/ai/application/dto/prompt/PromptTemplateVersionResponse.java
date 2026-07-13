package com.codeforge.ai.application.dto.prompt;

import java.time.LocalDateTime;

public record PromptTemplateVersionResponse(
        Long id,
        Long templateId,
        Integer versionNo,
        String versionStatus,
        String systemPrompt,
        String userPrompt,
        String variablesJson,
        String modelStrategyJson,
        Long publishedBy,
        LocalDateTime publishedAt
) {
}
