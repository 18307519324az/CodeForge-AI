package com.codeforge.ai.application.dto.publication;

import com.codeforge.ai.domain.app.enums.PublicationDownloadAvailability;
import java.time.LocalDateTime;

public record PublicAppDetailResponse(
        Long publicationId,
        String slug,
        String publicTitle,
        String publicDescription,
        String appType,
        String publisherDisplayName,
        Integer versionNo,
        String generationSource,
        Boolean allowPreview,
        Boolean allowDownload,
        PublicationDownloadAvailability downloadAvailability,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt,
        Long viewCount,
        Long downloadCount
) {
}
