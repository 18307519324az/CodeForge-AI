package com.codeforge.ai.application.dto.export;

import java.time.LocalDateTime;

public record ExportPackageCreateResponse(
        Long id,
        Long appId,
        Long appVersionId,
        Integer versionNo,
        String packageType,
        String status,
        String fileName,
        LocalDateTime createdAt
) {
}
