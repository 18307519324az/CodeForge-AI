package com.codeforge.ai.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MysqlFreshBootstrapProbeIntegrationTest {

    private static final DateTimeFormatter DB_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private String probeDatabaseName;
    private String adminUser;
    private String adminPassword;
    private String jdbcUrl;

    @BeforeAll
    void setUpProbeDatabase() throws Exception {
        adminUser = System.getenv("MYSQL_PROBE_ADMIN_USER");
        adminPassword = System.getenv("MYSQL_PROBE_ADMIN_PASSWORD");
        Assumptions.assumeTrue(adminUser != null && !adminUser.isBlank(), "MYSQL_PROBE_ADMIN_USER required");
        Assumptions.assumeTrue(adminPassword != null && !adminPassword.isBlank(), "MYSQL_PROBE_ADMIN_PASSWORD required");

        probeDatabaseName = "codeforge_flyway_baseline_probe_" + LocalDateTime.now().format(DB_TS);
        jdbcUrl = "jdbc:mysql://localhost:3306/" + probeDatabaseName
                + "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai";

        try (Connection connection = adminConnection("mysql");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + probeDatabaseName
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    @AfterAll
    void tearDownProbeDatabase() throws Exception {
        if (probeDatabaseName == null || adminUser == null) {
            return;
        }
        try (Connection connection = adminConnection("mysql");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + probeDatabaseName);
        }
    }

    @Test
    void freshMysqlDatabaseShouldBootstrapFromBaselineOnly() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, adminUser, adminPassword)
                .locations(MysqlFlywayLocations.MYSQL_PROFILE_LOCATIONS.split(","))
                .load();

        var migrateResult = flyway.migrate();
        assertThat(migrateResult.migrationsExecuted).isGreaterThan(0);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("33");

        MigrationInfo baseline = Arrays.stream(flyway.info().all())
                .filter(info -> info.getVersion() != null
                        && "33".equals(info.getVersion().getVersion())
                        && info.getType().name().contains("BASELINE"))
                .findFirst()
                .orElseThrow();
        assertThat(baseline.getState()).isIn(MigrationState.SUCCESS, MigrationState.BASELINE);
        assertThat(baseline.getScript()).contains(MysqlFlywayLocations.BASELINE_SCRIPT);

        assertThat(tableExists("user")).isTrue();
        assertThat(tableExists("generation_task")).isTrue();
        assertThat(historyContainsVersion("1")).isFalse();
        assertThat(columnType("generated_file", "file_path")).contains("varchar(1024)");
        assertThat(indexDefinition("generated_file", "idx_generated_file_version_path")).contains("file_path`(255)");
    }

    private Connection adminConnection(String database) throws Exception {
        return DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/" + database
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
                adminUser,
                adminPassword);
    }

    private boolean tableExists(String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema = ? AND table_name = ?
                     """)) {
            statement.setString(1, probeDatabaseName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean historyContainsVersion(String version) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = 1")) {
            statement.setString(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private String columnType(String tableName, String columnName) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COLUMN_TYPE FROM information_schema.columns
                     WHERE table_schema = ? AND table_name = ? AND column_name = ?
                     """)) {
            statement.setString(1, probeDatabaseName);
            statement.setString(2, tableName);
            statement.setString(3, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1).toLowerCase(Locale.ROOT);
            }
        }
    }

    private String indexDefinition(String tableName, String indexName) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
             PreparedStatement statement = connection.prepareStatement("SHOW CREATE TABLE " + tableName)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(2);
            }
        }
    }
}
