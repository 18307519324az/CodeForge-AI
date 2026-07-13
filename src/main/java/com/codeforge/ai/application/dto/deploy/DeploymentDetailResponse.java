package com.codeforge.ai.application.dto.deploy;

import java.time.LocalDateTime;

public record DeploymentDetailResponse(
        Long id,
        Long appId,
        Long appVersionId,
        String environmentCode,
        String deployTarget,
        String deployStatus,
        String runtimeConfigJson,
        String requestId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
