package com.codeforge.ai.application.dto.admin;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long workspaceId,
        Long operatorUserId,
        String actionType,
        String targetType,
        String targetId,
        String requestId,
        LocalDateTime createdAt
) {
}
