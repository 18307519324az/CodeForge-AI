package com.codeforge.ai.application.dto.app;

public record AppVersionFileResponse(
        Long id,
        Long appVersionId,
        String filePath,
        String fileName,
        String fileType,
        String contentHash,
        Long fileSize
) {
}
