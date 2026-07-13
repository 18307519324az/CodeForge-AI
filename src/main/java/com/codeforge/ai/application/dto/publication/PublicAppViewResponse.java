package com.codeforge.ai.application.dto.publication;

public record PublicAppViewResponse(
        boolean counted,
        Long viewCount
) {
}
