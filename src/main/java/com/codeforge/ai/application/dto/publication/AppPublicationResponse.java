package com.codeforge.ai.application.dto.publication;

import java.time.LocalDateTime;

public record AppPublicationResponse(
        Long publicationId,
        Long appId,
        Long versionId,
        Integer versionNo,
        String slug,
        String status,
        String publicTitle,
        String publicDescription,
        Boolean allowPreview,
        Boolean allowDownload,
        LocalDateTime publishedAt,
        LocalDateTime unpublishedAt,
        Long viewCount,
        Long downloadCount
) {
}
