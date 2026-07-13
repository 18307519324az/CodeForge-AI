package com.codeforge.ai.application.dto.publication;

public record PublicDownloadTokenResponse(
        String downloadUrl,
        long expiresInSeconds
) {
}
