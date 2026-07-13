package com.codeforge.ai.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationSmokeTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:flyway_migration_smoke;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.*\\.sql$");

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(JDBC_URL, "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void shouldApplyAllMigrationScriptsWithValidOrdering() throws SQLException, IOException {
        List<Integer> expectedVersions = collectMigrationVersions();
        assertThat(expectedVersions).isNotEmpty();
        assertThat(new java.util.HashSet<>(expectedVersions)).hasSize(expectedVersions.size());
        assertThat(isStrictlyIncreasing(expectedVersions)).isTrue();
        assertThat(Files.readString(Path.of("sql/migrations/V1__init.sql"))).isNotBlank();
        assertThat(Files.readString(Path.of("src/test/resources/sql/test-migrations/V31__workspace_owner_name_unique_for_test.sql")))
                .isNotBlank();

        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:sql/migrations", "filesystem:sql/h2-test", "classpath:sql/test-migrations")
                .baselineOnMigrate(true)
                .load();

        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(expectedVersions.size());
        assertThat(tableExists("flyway_schema_history")).isTrue();
        assertThat(tableExists("workspace")).isTrue();
        assertThat(tableExists("generation_task")).isTrue();
        assertThat(tableExists("model_call_log")).isTrue();
        assertThat(tableExists("deployment_job")).isTrue();
        assertThat(tableExists("generation_message")).isTrue();
        assertThat(tableExists("app_like")).isTrue();
        assertThat(tableExists("app_publication")).isTrue();
        assertThat(appliedVersions()).containsAll(expectedVersions);
        assertThat(appliedVersions()).contains(31);
        assertThat(appliedVersions()).contains(32);
        assertThat(appliedVersions()).contains(33);
        assertThat(tableExists("model_provider_credential")).isTrue();
        assertThat(tableExists("ai_routing_config")).isTrue();
    }

    private List<Integer> collectMigrationVersions() throws IOException {
        List<Integer> versions = new ArrayList<>();
        versions.addAll(readVersionsFromDirectory(Path.of("sql/migrations")));
        versions.addAll(readVersionsFromDirectory(Path.of("sql/h2-test")));
        versions.addAll(readVersionsFromDirectory(Path.of("src/test/resources/sql/test-migrations")));
        versions.sort(Comparator.naturalOrder());
        return versions;
    }

    private List<Integer> readVersionsFromDirectory(Path directory) throws IOException {
        List<Integer> versions = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .forEach(path -> versions.add(parseVersion(path.getFileName().toString())));
        }
        return versions;
    }

    private int parseVersion(String filename) {
        Matcher matcher = VERSION_PATTERN.matcher(filename);
        assertThat(matcher.matches()).as("migration filename %s", filename).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private boolean isStrictlyIncreasing(List<Integer> versions) {
        for (int i = 1; i < versions.size(); i++) {
            if (versions.get(i) <= versions.get(i - 1)) {
                return false;
            }
        }
        return true;
    }

    private List<Integer> appliedVersions() throws SQLException {
        List<Integer> versions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version FROM flyway_schema_history WHERE type = 'SQL' ORDER BY installed_rank")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    versions.add(Integer.parseInt(resultSet.getString("version")));
                }
            }
        }
        return versions;
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """)) {
            preparedStatement.setString(1, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }
}
