package com.codeforge.ai.application.dto.user;

import java.time.LocalDateTime;
import java.util.List;

public record CurrentUserResponse(
        Long id,
        String account,
        String displayName,
        String avatarUrl,
        String email,
        String phone,
        String status,
        LocalDateTime lastLoginAt,
        List<String> platformRoles
) {
}
