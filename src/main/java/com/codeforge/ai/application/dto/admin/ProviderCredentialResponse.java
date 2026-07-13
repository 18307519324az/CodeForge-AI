package com.codeforge.ai.application.dto.admin;

public record ProviderCredentialResponse(
        boolean configured,
        String source,
        String maskedHint
) {
}
