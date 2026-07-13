package com.codeforge.ai.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayBaselineBehaviorTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:flyway_baseline_behavior;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER";
    private static final String TEMP_LOCATIONS =
            "classpath:flyway-baseline-behavior-test/versioned,"
                    + "classpath:flyway-baseline-behavior-test/baseline";

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(JDBC_URL, "sa", "");
        try (var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void freshDatabaseShouldApplyBaselineAndIgnoreLowerVersionedMigrations() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations(TEMP_LOCATIONS.split(","))
                .baselineOnMigrate(false)
                .load();

        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isEqualTo(2);

        assertThat(tableExists("baseline_marker")).isTrue();
        assertThat(tableExists("legacy_v1_marker")).isFalse();
        assertThat(tableExists("legacy_v33_marker")).isFalse();
        assertThat(tableExists("after_baseline_marker")).isTrue();

        List<HistoryRow> history = readHistory();
        assertThat(history).extracting(HistoryRow::version)
                .contains("33", "34");
        assertThat(history).extracting(HistoryRow::version)
                .doesNotContain("1");

        MigrationInfo baseline = Arrays.stream(flyway.info().all())
                .filter(info -> "33".equals(versionOf(info)) && info.getType().name().contains("BASELINE"))
                .findFirst()
                .orElseThrow();
        assertThat(baseline.getState()).isIn(MigrationState.SUCCESS, MigrationState.BASELINE);

        var versionedV33 = Arrays.stream(flyway.info().all())
                .filter(info -> "33".equals(versionOf(info)) && info.getType().name().contains("VERSIONED"))
                .findFirst();
        if (versionedV33.isPresent()) {
            assertThat(versionedV33.get().getState()).isEqualTo(MigrationState.IGNORED);
        }

        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("34");
    }

    @Test
    void existingVersionedHistoryShouldIgnoreBaselineMigration() throws Exception {
        Flyway versionedOnly = Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("classpath:flyway-baseline-behavior-test/versioned")
                .load();
        versionedOnly.migrate();
        assertThat(tableExists("legacy_v1_marker")).isTrue();

        Flyway withBaseline = Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations(TEMP_LOCATIONS.split(","))
                .load();

        var secondMigrate = withBaseline.migrate();
        assertThat(secondMigrate.migrationsExecuted).isZero();
        assertThat(tableExists("baseline_marker")).isFalse();

        MigrationInfo baseline = Arrays.stream(withBaseline.info().all())
                .filter(info -> info.getType().name().contains("BASELINE"))
                .findFirst()
                .orElseThrow();
        assertThat(baseline.getState()).isEqualTo(MigrationState.IGNORED);
    }

    private static String versionOf(MigrationInfo info) {
        return info.getVersion() == null ? null : info.getVersion().getVersion();
    }

    private boolean tableExists(String tableName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private List<HistoryRow> readHistory() throws Exception {
        List<HistoryRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version, type, script, success
                FROM flyway_schema_history
                ORDER BY installed_rank
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new HistoryRow(
                            resultSet.getString("version"),
                            resultSet.getString("type"),
                            resultSet.getString("script"),
                            resultSet.getInt("success") == 1));
                }
            }
        }
        return rows;
    }

    private record HistoryRow(String version, String type, String script, boolean success) {
    }
}
