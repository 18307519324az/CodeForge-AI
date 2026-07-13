package com.codeforge.ai.application.dto.app;

public record AppVersionDiffFileResponse(
        String filePath,
        String changeType,
        String fromContentHash,
        String toContentHash,
        Long fromFileSize,
        Long toFileSize
) {
}
