package com.codeforge.ai.application.dto.app;

public record AppVersionRollbackResponse(
        Long appId,
        Long versionId,
        Integer versionNo,
        String status
) {
}
