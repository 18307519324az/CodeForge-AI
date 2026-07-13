package com.codeforge.ai.application.dto.app;

import java.time.LocalDateTime;

public record AppVersionListItemResponse(
        Long id,
        Integer versionNo,
        String versionSource,
        Long sourceTaskId,
        String changeSummary,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {
}
