package com.codeforge.ai.domain.prompt.model;

public record ResolvedGenerationPrompt(
        Long templateId,
        Long templateVersionId,
        String templateCode,
        Integer versionNo,
        String renderedSystemPrompt,
        String renderedUserPrompt,
        String systemPromptSha256,
        String userPromptSha256,
        String combinedPromptFingerprint
) {
}
