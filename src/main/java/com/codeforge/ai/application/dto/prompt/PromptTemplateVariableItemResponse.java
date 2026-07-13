package com.codeforge.ai.application.dto.prompt;

public record PromptTemplateVariableItemResponse(
        String key,
        String type,
        boolean required,
        String description
) {
}
