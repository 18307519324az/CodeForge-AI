package com.codeforge.ai.application.dto.app;

import java.time.LocalDateTime;

public record AppVersionDetailResponse(
        Long id,
        Long appId,
        Integer versionNo,
        String versionSource,
        Long sourceTaskId,
        String changeSummary,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
