package com.codeforge.ai.infrastructure.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codeforge.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String secret,
        @Min(60) long accessTokenExpireSeconds
) {
}
