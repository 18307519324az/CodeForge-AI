package com.codeforge.ai.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationTaskIdempotencyConstraintTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:generation_task_idem;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE generation_task (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        workspace_id BIGINT NOT NULL,
                        app_id BIGINT NOT NULL,
                        idempotency_key VARCHAR(128) NULL,
                        CONSTRAINT uk_generation_task_idem_key UNIQUE (idempotency_key)
                    )
                    """);
            statement.execute("""
                    ALTER TABLE generation_task
                    DROP CONSTRAINT uk_generation_task_idem_key
                    """);
            statement.execute("""
                    ALTER TABLE generation_task
                    ADD CONSTRAINT uk_generation_task_scope_idem_key UNIQUE (workspace_id, app_id, idempotency_key)
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void shouldRejectDuplicateIdempotencyKeyInSameWorkspaceAndApp() throws SQLException {
        insertRow(1001L, 2001L, "idem-key-1");

        assertThatThrownBy(() -> insertRow(1001L, 2001L, "idem-key-1"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldAllowSameIdempotencyKeyAcrossDifferentScopes() throws SQLException {
        insertRow(1001L, 2001L, "idem-key-2");

        assertThatCode(() -> insertRow(1002L, 2001L, "idem-key-2")).doesNotThrowAnyException();
        assertThatCode(() -> insertRow(1001L, 2002L, "idem-key-2")).doesNotThrowAnyException();
    }

    private void insertRow(Long workspaceId, Long appId, String idempotencyKey) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(String.format(
                    "INSERT INTO generation_task(workspace_id, app_id, idempotency_key) VALUES (%d, %d, '%s')",
                    workspaceId,
                    appId,
                    idempotencyKey));
        }
    }
}
