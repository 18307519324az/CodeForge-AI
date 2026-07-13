package com.codeforge.ai.domain.generation;

public record GenerationContext(
    String requirement,
    String appName,
    String appType,
    String codeGenType,
    Long appId,
    Long userId,
    Long taskId,
    Long sessionId,
    String providerCode,
    String modelName,
    String baseUrl,
    String apiKey,
    String systemPrompt,
    String renderedUserPrompt,
    Long promptTemplateId,
    Long promptTemplateVersionId,
    String promptTemplateCode,
    Integer promptTemplateVersionNo
) {
    public GenerationContext {
        systemPrompt = systemPrompt != null ? systemPrompt : "You are a code generator. Generate project files based on the user requirement.";
    }

    /** Backward-compatible short constructor for callers that don't supply a system prompt. */
    public GenerationContext(
        String requirement, String appName, String appType, String codeGenType,
        Long appId, Long userId, Long taskId,
        Long sessionId,
        String providerCode, String modelName, String baseUrl, String apiKey
    ) {
        this(requirement, appName, appType, codeGenType, appId, userId, taskId, sessionId,
             providerCode, modelName, baseUrl, apiKey, null,
             null, null, null, null, null);
    }

    public GenerationContext(
        String requirement, String appName, String appType, String codeGenType,
        Long appId, Long userId, Long taskId,
        Long sessionId,
        String providerCode, String modelName, String baseUrl, String apiKey,
        String systemPrompt
    ) {
        this(requirement, appName, appType, codeGenType, appId, userId, taskId, sessionId,
             providerCode, modelName, baseUrl, apiKey, systemPrompt,
             null, null, null, null, null);
    }

    public boolean usesTemplatePrompt() {
        return promptTemplateVersionId != null;
    }
}
