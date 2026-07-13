package com.codeforge.ai.application.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserListItemResponse(
        Long id,
        String account,
        String displayName,
        String email,
        String status,
        List<String> platformRoles,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
}
