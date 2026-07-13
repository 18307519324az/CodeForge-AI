package com.codeforge.ai.application.dto.workspace;

import java.time.LocalDateTime;

public record WorkspaceMemberResponse(
        Long memberId,
        Long userId,
        String account,
        String displayName,
        String memberRole,
        String memberStatus,
        LocalDateTime joinedAt
) {
}
