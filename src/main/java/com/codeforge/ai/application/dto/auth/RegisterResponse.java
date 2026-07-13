package com.codeforge.ai.application.dto.auth;

import com.codeforge.ai.application.dto.user.CurrentUserResponse;
import java.util.List;

public record RegisterResponse(
        CurrentUserResponse user,
        List<String> platformRoles
) {
}
