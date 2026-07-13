package com.codeforge.ai.application.dto.app;

public record AppVersionFileContentResponse(
        Long versionId,
        String filePath,
        String fileName,
        String fileType,
        String content
) {
}
