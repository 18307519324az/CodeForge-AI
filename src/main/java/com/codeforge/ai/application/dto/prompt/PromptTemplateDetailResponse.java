package com.codeforge.ai.application.dto.prompt;

public record PromptTemplateDetailResponse(
        Long id,
        Long workspaceId,
        String templateName,
        String templateScene,
        String status,
        Integer currentVersionNo,
        String remark,
        PromptTemplateVersionResponse currentVersion
) {
}
