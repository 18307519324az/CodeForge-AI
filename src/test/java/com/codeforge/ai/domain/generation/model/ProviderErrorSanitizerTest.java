package com.codeforge.ai.domain.generation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderErrorSanitizerTest {

    @Test
    void shouldMaskApiKeyAndBearerToken() {
        String sanitized = ProviderErrorSanitizer.sanitize(
                "401 Unauthorized Bearer sk-test12345678901234567890123456789012 api_key=secret-value");

        assertThat(sanitized).doesNotContain("sk-test12345678901234567890123456789012");
        assertThat(sanitized).doesNotContain("secret-value");
        assertThat(sanitized).contains("Bearer ***");
    }

    @Test
    void shouldNotLeakAuthorizationInHealthCheckMessage() {
        ProviderHealthCheckService.ProviderHealthCheckResult result =
                ProviderHealthCheckService.ProviderHealthCheckResult.unhealthy(
                        "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test");

        assertThat(result.message()).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
    }
}
