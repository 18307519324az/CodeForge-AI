package com.codeforge.ai.application.dto.app;

import java.time.LocalDateTime;

public record AiAppDetailResponse(
        Long id,
        Long workspaceId,
        String name,
        String description,
        String coverUrl,
        String appType,
        String status,
        String visibility,
        Long currentVersionId,
        Long latestTaskId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer currentVersionNo,
        String latestGenerationSource,
        Integer generatedFileCount,
        String latestExportStatus,
        String displayStatus,
        String publicationStatus,
        String publicationSlug,
        Long publicationId
) {
}
