package com.codeforge.ai.application.dto.admin;

import java.time.LocalDateTime;

public record ModelProviderResponse(
        Long id,
        String providerCode,
        String providerName,
        String baseUrl,
        String authMode,
        String secretRef,
        String status,
        Integer priority,
        String defaultModel,
        String credentialSource,
        boolean configured,
        String maskedHint,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean apiKeyConfigured() {
        return configured;
    }
}
