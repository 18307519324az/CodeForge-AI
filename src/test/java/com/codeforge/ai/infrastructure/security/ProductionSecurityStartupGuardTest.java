package com.codeforge.ai.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityStartupGuardTest {

    @Test
    void shouldBlockDefaultJwtSecretOnProdProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("codeforge.security.jwt.secret", ProductionSecurityStartupGuard.DEFAULT_JWT_SECRET);
        ProductionSecurityStartupGuard guard = new ProductionSecurityStartupGuard(environment);

        assertThatThrownBy(() -> guard.onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void shouldAllowCustomJwtSecretOnProdProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("codeforge.security.jwt.secret", "prod-only-secret-value-32chars-min");
        ProductionSecurityStartupGuard guard = new ProductionSecurityStartupGuard(environment);

        assertThatCode(() -> guard.onApplicationEvent(null)).doesNotThrowAnyException();
    }
}
