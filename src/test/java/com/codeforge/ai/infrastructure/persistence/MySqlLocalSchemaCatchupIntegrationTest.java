package com.codeforge.ai.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies idempotent MySQL-only schema catch-up for local canonical development.
 * Requires local profile with reachable MySQL. Creates a logical mysqldump backup first.
 */
@SpringBootTest
@ActiveProfiles("test")
class MySqlLocalSchemaCatchupIntegrationTest {

    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Test
    void shouldBackupAndApplyPromptTraceColumnsIdempotently() throws Exception {
        Assumptions.assumeTrue(isMySqlRuntime(), "local MySQL runtime required");

        Path backupFile = createMysqldumpBackup();
        assertThat(backupFile).exists();
        assertThat(Files.size(backupFile)).isGreaterThan(0L);

        Map<String, Long> beforeCounts = readCoreCounts();
        assertThat(beforeCounts.get("ai_app")).isGreaterThanOrEqualTo(1L);

        runSqlFile(Path.of("sql/mysql-local/V28_1__mysql_model_call_log_prompt_trace.sql"));
        executeCatchupHistoryScript();

        assertColumnExists("model_call_log", "prompt_template_version_id");
        assertColumnExists("model_call_log", "prompt_template_code");
        assertColumnExists("model_call_log", "prompt_template_version_no");

        Map<String, Long> afterCounts = readCoreCounts();
        assertThat(afterCounts.get("ai_app")).isEqualTo(beforeCounts.get("ai_app"));
        assertThat(afterCounts.get("generation_task")).isEqualTo(beforeCounts.get("generation_task"));
        assertThat(afterCounts.get("app_version")).isEqualTo(beforeCounts.get("app_version"));
    }

    private boolean isMySqlRuntime() {
        String jdbcUrl = environment.getProperty("spring.datasource.url", "");
        return jdbcUrl.toLowerCase().contains("mysql");
    }

    private Path createMysqldumpBackup() throws IOException, InterruptedException {
        Path backupDir = Path.of("D:", "Projects", "backups");
        Files.createDirectories(backupDir);
        Path backupFile = backupDir.resolve("codeforge_ai-" + BACKUP_TS.format(LocalDateTime.now()) + ".sql");

        String host = environment.getProperty("DB_HOST", "127.0.0.1");
        String port = environment.getProperty("DB_PORT", "3306");
        String database = environment.getProperty("DB_NAME", "codeforge_ai");
        String username = environment.getProperty("spring.datasource.username", "codeforge_ai_user");
        String password = environment.getProperty("spring.datasource.password", "");

        ProcessBuilder builder = new ProcessBuilder(
                "mysqldump",
                "-h" + host,
                "-P" + port,
                "-u" + username,
                "--single-transaction",
                "--routines",
                "--triggers",
                database
        );
        builder.environment().put("MYSQL_PWD", password);
        builder.redirectOutput(backupFile.toFile());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();
        int exitCode = process.waitFor();
        Assumptions.assumeTrue(exitCode == 0 && Files.exists(backupFile) && Files.size(backupFile) > 0,
                "mysqldump backup failed; configure DB credentials for local profile");

        return backupFile;
    }

    private void runSqlFile(Path sqlFile) throws Exception {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.FileSystemResource(sqlFile.toFile()));
        }
    }

    private void executeCatchupHistoryScript() throws Exception {
        Path catchupScript = Path.of("scripts", "mysql-flyway-catchup.sql");
        Assumptions.assumeTrue(Files.exists(catchupScript), "scripts/mysql-flyway-catchup.sql missing");
        String sql = Files.readString(catchupScript);
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                jdbcTemplate.execute(trimmed);
            }
        }
    }

    private Map<String, Long> readCoreCounts() {
        return Map.of(
                "ai_app", countTable("ai_app"),
                "generation_task", countTable("generation_task"),
                "app_version", countTable("app_version")
        );
    }

    private long countTable(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }

    private void assertColumnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        assertThat(count).isNotNull().isGreaterThan(0);
    }
}
