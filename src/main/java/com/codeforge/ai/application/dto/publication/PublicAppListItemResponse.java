package com.codeforge.ai.application.dto.publication;

import com.codeforge.ai.domain.app.enums.PublicationDownloadAvailability;
import java.time.LocalDateTime;

public record PublicAppListItemResponse(
        Long publicationId,
        String slug,
        String publicTitle,
        String publicDescription,
        String appType,
        String publisherDisplayName,
        Integer versionNo,
        Boolean allowPreview,
        Boolean allowDownload,
        PublicationDownloadAvailability downloadAvailability,
        LocalDateTime publishedAt,
        Long viewCount,
        Long downloadCount
) {
}
