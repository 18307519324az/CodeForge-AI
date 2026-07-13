package com.codeforge.ai.application.dto.deploy;

import java.time.LocalDateTime;

public record DeploymentCreateResponse(
        Long id,
        Long appId,
        Long appVersionId,
        String environmentCode,
        String deployTarget,
        String deployStatus,
        String requestId,
        LocalDateTime createdAt
) {
}
