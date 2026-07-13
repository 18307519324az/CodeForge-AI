package com.codeforge.ai.infrastructure.security;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public record CurrentUser(
        Long userId,
        String account,
        List<String> platformRoles
) implements Serializable {

    public CurrentUser {
        platformRoles = platformRoles == null ? List.of() : List.copyOf(platformRoles);
    }

    public boolean hasRole(String roleCode) {
        return platformRoles.stream().anyMatch(roleCode::equals);
    }

    public boolean isPlatformAdmin() {
        return hasRole("PLATFORM_ADMIN");
    }

    public Long requiredUserId() {
        return Objects.requireNonNull(userId, "userId must not be null");
    }
}
