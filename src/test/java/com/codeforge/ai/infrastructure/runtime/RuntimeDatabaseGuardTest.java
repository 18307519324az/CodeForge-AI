package com.codeforge.ai.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuntimeDatabaseGuardTest {

    @Test
    void shouldDetectMysqlIdentityFromJdbcUrl() {
        String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/codeforge_ai?useUnicode=true";

        assertThat(RuntimeDatabaseGuard.detectDatabaseType(jdbcUrl)).isEqualTo("MYSQL");
        assertThat(RuntimeDatabaseGuard.extractHost(jdbcUrl)).isEqualTo("127.0.0.1:3306");
        assertThat(RuntimeDatabaseGuard.extractDatabaseName(jdbcUrl)).isEqualTo("codeforge_ai");
        assertThat(RuntimeDatabaseGuard.extractDatabasePath(jdbcUrl)).isEqualTo("-");
    }

    @Test
    void shouldDetectH2FileIdentityFromJdbcUrl() {
        String jdbcUrl = "jdbc:h2:file:./.local-data/codeforge-dev;MODE=MySQL";

        assertThat(RuntimeDatabaseGuard.detectDatabaseType(jdbcUrl)).isEqualTo("H2_FILE");
        assertThat(RuntimeDatabaseGuard.extractDatabasePath(jdbcUrl)).isEqualTo("./.local-data/codeforge-dev");
        assertThat(RuntimeDatabaseGuard.extractDatabaseName(jdbcUrl)).isEqualTo("-");
    }

    @Test
    void shouldSanitizePasswordInJdbcUrl() {
        String sanitized = RuntimeDatabaseGuard.sanitizeJdbcUrl("jdbc:mysql://localhost/db?password=secret&user=u");

        assertThat(sanitized).contains("password=***");
        assertThat(sanitized).doesNotContain("secret");
    }
}
