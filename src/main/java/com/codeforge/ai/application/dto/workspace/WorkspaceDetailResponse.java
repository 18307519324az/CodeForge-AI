package com.codeforge.ai.application.dto.workspace;

public record WorkspaceDetailResponse(
        Long id,
        String name,
        String description,
        Long ownerUserId,
        String status,
        String planCode,
        String memberRole
) {
}
