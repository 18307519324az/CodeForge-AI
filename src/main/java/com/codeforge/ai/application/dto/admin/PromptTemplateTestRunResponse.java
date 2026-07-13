package com.codeforge.ai.application.dto.admin;

public record PromptTemplateTestRunResponse(
        Long templateId,
        Long promptTemplateVersionId,
        String promptTemplateCode,
        Integer promptTemplateVersionNo,
        boolean testRun,
        String outputPreview,
        long latencyMs
) {
}
