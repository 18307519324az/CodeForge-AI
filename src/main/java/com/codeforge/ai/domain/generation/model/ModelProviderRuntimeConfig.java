package com.codeforge.ai.domain.generation.model;

public record ModelProviderRuntimeConfig(
        Long providerId,
        String providerCode,
        String providerName,
        String baseUrl,
        String authMode,
        String apiProtocol,
        String apiKeyEnv,
        String defaultModel,
        Integer routingPriority,
        boolean active
) {
}
