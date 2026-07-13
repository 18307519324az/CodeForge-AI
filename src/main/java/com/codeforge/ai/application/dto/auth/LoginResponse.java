package com.codeforge.ai.application.dto.auth;

import com.codeforge.ai.application.dto.user.CurrentUserResponse;
import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        CurrentUserResponse user,
        List<String> platformRoles
) {
}
