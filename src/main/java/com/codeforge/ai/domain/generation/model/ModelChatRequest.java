package com.codeforge.ai.domain.generation.model;

import java.util.List;

public class ModelChatRequest {
    private static final int DEFAULT_MAX_TOKENS = 8192;
    private static final double DEFAULT_TEMPERATURE = 0.2d;

    private String providerCode;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private List<ModelMessage> messages;
    private Integer maxTokens;
    private Double temperature;
    private Boolean stream;
    private Long appId;
    private Long taskId;
    private Long userId;

    public static ModelChatRequest of(String providerCode, String baseUrl, String apiKey,
            String modelName, List<ModelMessage> messages, Long appId, Long taskId, Long userId) {
        return of(providerCode, baseUrl, apiKey, modelName, messages, appId, taskId, userId,
                DEFAULT_MAX_TOKENS, DEFAULT_TEMPERATURE);
    }

    public static ModelChatRequest of(String providerCode, String baseUrl, String apiKey,
            String modelName, List<ModelMessage> messages, Long appId, Long taskId, Long userId,
            Integer maxTokens, Double temperature) {
        ModelChatRequest r = new ModelChatRequest();
        r.providerCode = providerCode; r.baseUrl = baseUrl; r.apiKey = apiKey;
        r.modelName = modelName; r.messages = messages; r.appId = appId;
        r.taskId = taskId; r.userId = userId;
        r.maxTokens = maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS;
        r.temperature = temperature != null ? temperature : DEFAULT_TEMPERATURE;
        r.stream = false;
        return r;
    }

    public static ModelChatRequest ofStream(String providerCode, String baseUrl, String apiKey,
            String modelName, List<ModelMessage> messages, Long appId, Long taskId, Long userId) {
        ModelChatRequest r = of(providerCode, baseUrl, apiKey, modelName, messages, appId, taskId, userId);
        r.stream = true;
        return r;
    }

    public String providerCode() { return providerCode; }
    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String modelName() { return modelName; }
    public List<ModelMessage> messages() { return messages; }
    public Integer maxTokens() { return maxTokens; }
    public Double temperature() { return temperature; }
    public Boolean stream() { return stream; }
    public Long appId() { return appId; }
    public Long taskId() { return taskId; }
    public Long userId() { return userId; }
}
