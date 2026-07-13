package com.codeforge.ai.application.dto.publication;

public record PublicAppQueryRequest(
        Long pageNo,
        Long pageSize,
        String keyword,
        String appType,
        String sort
) {
}
