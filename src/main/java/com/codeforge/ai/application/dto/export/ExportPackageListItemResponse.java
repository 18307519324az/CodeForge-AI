package com.codeforge.ai.application.dto.export;

import java.time.LocalDateTime;

public record ExportPackageListItemResponse(
        Long id,
        Long appId,
        Long appVersionId,
        String packageType,
        String status,
        String fileName,
        LocalDateTime createdAt
) {
}
