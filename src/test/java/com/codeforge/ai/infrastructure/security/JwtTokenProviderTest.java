package com.codeforge.ai.infrastructure.security;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    @Test
    void shouldCreateAndParseAccessToken() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
                "codeforge-ai",
                "change-this-jwt-secret-change-this-jwt-secret",
                7200
        ));

        CurrentUser currentUser = new CurrentUser(1001L, "tester", List.of("USER", "PLATFORM_ADMIN"));
        String token = jwtTokenProvider.createAccessToken(currentUser);

        assertThat(jwtTokenProvider.isValidToken(token)).isTrue();
        CurrentUser parsed = jwtTokenProvider.parseCurrentUser(token);
        assertThat(parsed.userId()).isEqualTo(1001L);
        assertThat(parsed.account()).isEqualTo("tester");
        assertThat(parsed.platformRoles()).containsExactly("USER", "PLATFORM_ADMIN");
    }
}
