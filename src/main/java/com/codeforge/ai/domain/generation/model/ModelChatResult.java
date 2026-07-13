package com.codeforge.ai.domain.generation.model;

public class ModelChatResult {
    private String content;
    private String finishReason;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Long latencyMs;
    private String providerCode;
    private String modelName;

    public static ModelChatResult success(String content, String finishReason, Long promptTokens,
            Long completionTokens, Long totalTokens, Long latencyMs, String providerCode, String modelName) {
        ModelChatResult r = new ModelChatResult();
        r.content = content; r.finishReason = finishReason; r.promptTokens = promptTokens;
        r.completionTokens = completionTokens; r.totalTokens = totalTokens; r.latencyMs = latencyMs;
        r.providerCode = providerCode; r.modelName = modelName;
        return r;
    }

    public String content() { return content; }
    public String finishReason() { return finishReason; }
    public Long promptTokens() { return promptTokens; }
    public Long completionTokens() { return completionTokens; }
    public Long totalTokens() { return totalTokens; }
    public Long latencyMs() { return latencyMs; }
    public String providerCode() { return providerCode; }
    public String modelName() { return modelName; }
}
