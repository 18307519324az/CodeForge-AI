package com.codeforge.ai.application.dto.admin;

import java.time.LocalDateTime;

public record ProviderHealthCheckResponse(
        Long providerId,
        String providerCode,
        boolean healthy,
        String message,
        LocalDateTime checkedAt
) {
}
