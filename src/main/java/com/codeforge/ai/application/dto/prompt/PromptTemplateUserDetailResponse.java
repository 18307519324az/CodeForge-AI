package com.codeforge.ai.application.dto.prompt;

import java.util.List;

public record PromptTemplateUserDetailResponse(
        Long id,
        String templateName,
        String description,
        String templateScene,
        String templateSceneLabel,
        String applicableAppType,
        String exampleRequirement,
        List<PromptTemplateVariableItemResponse> variables,
        PromptTemplatePublishedVersionResponse publishedVersion
) {
}
