package com.codeforge.ai.application.dto.app;

public record AppVersionPreviewTokenResponse(
        String previewUrl,
        long expiresInSeconds
) {
}
