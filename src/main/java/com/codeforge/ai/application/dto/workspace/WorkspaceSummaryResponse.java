package com.codeforge.ai.application.dto.workspace;

public record WorkspaceSummaryResponse(
        Long id,
        String name,
        String description,
        Long ownerUserId,
        String status,
        String planCode,
        String memberRole
) {
}
