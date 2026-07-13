package com.codeforge.ai.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FlywayMigrationTopologyTest {

    private static final String MYSQL_LOCATIONS = MysqlFlywayLocations.MYSQL_PROFILE_LOCATIONS;
    private static final String TEST_LOCATIONS =
            "filesystem:sql/migrations,filesystem:sql/h2-test,classpath:sql/test-migrations";
    private static final Pattern MIGRATION_FILENAME =
            Pattern.compile("^([BRVU]?)([\\d_]+)__.*\\.sql$", Pattern.CASE_INSENSITIVE);

    @Test
    void mysqlProfileLocationsShouldHaveUniqueVersions() throws IOException {
        List<String> migrationFiles = collectSqlFilenames(
                Path.of("sql/migrations"),
                Path.of("sql/mysql-local"),
                Path.of("sql/mysql-baseline"));
        assertNoDuplicateMigrationKeys(migrationFiles, "mysql-static");
        assertThat(migrationFiles).noneMatch(name -> name.contains("h2-test"));
        assertThat(migrationFiles).noneMatch(name -> name.contains("test-migrations"));
        assertThat(migrationFiles).anyMatch(name -> name.startsWith("B33__"));
        assertThat(migrationFiles).anyMatch(name -> name.startsWith("V28_1__"));
        assertThat(migrationFiles).anyMatch(name -> name.startsWith("V30_1__"));
        assertThat(migrationFiles).noneMatch(name -> name.equals("V32__provider_configuration_center.sql"));
        assertThat(migrationFiles).noneMatch(name -> name.equals("V33__generation_task_prompt_template_binding.sql"));
    }

    @Test
    void testProfileLocationsShouldHaveUniqueVersions() throws IOException {
        List<String> migrationFiles = collectSqlFilenames(
                Path.of("sql/migrations"),
                Path.of("sql/h2-test"),
                Path.of("src/test/resources/sql/test-migrations"));
        assertNoDuplicateMigrationKeys(migrationFiles, "test-static");
        assertThat(migrationFiles).noneMatch(name -> name.contains("mysql-local"));
        assertThat(migrationFiles).anyMatch(name -> name.equals("V31__workspace_owner_name_unique_for_test.sql"));
        assertThat(migrationFiles).anyMatch(name -> name.equals("V32__provider_configuration_center.sql"));
        assertThat(migrationFiles).anyMatch(name -> name.equals("V33__generation_task_prompt_template_binding.sql"));
        assertThat(migrationFiles).noneMatch(name -> name.contains("_mysql.sql"));
    }

    @Test
    void mysqlFlywayResolverShouldExposeCanonicalCatchUpSequence() {
        List<String> versions = resolvedSqlVersions(MYSQL_LOCATIONS);
        assertThat(versions).contains("28", "28.1", "29", "30", "30.1", "32", "33");
        assertThat(versions.stream().filter("33"::equals).count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testFlywayResolverShouldLoadH2VariantsWithoutMysqlDialect() {
        List<String> versions = resolvedSqlVersions(TEST_LOCATIONS);
        assertThat(versions).doesNotHaveDuplicates();
        assertThat(versions).contains("31", "32", "33");
        assertThat(versions).doesNotContain("28.1", "30.1");
        assertThat(versions.stream().filter(v -> v.contains("mysql")).toList()).isEmpty();
    }

    @Test
    void mysqlAndTestResolversShouldResolveWithoutDuplicateVersionErrors() {
        assertThatCode(() -> resolvedSqlVersions(MYSQL_LOCATIONS)).doesNotThrowAnyException();
        assertThatCode(() -> resolvedSqlVersions(TEST_LOCATIONS)).doesNotThrowAnyException();
    }

    @Test
    void mysqlCatchUpVersionsShouldSortAfterV27InFlywayOrder() {
        List<MigrationVersion> ordered = Arrays.stream(Flyway.configure()
                        .dataSource("jdbc:h2:mem:order-topology;DB_CLOSE_DELAY=-1", "sa", "")
                        .locations(MYSQL_LOCATIONS.split(","))
                        .load()
                        .info()
                        .all())
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .sorted(Comparator.naturalOrder())
                .toList();

        MigrationVersion v27 = ordered.stream()
                .filter(version -> "27".equals(version.getVersion()))
                .findFirst()
                .orElseThrow();
        List<MigrationVersion> afterV27 = ordered.stream()
                .filter(version -> version.compareTo(v27) > 0)
                .toList();

        assertThat(afterV27.stream().map(MigrationVersion::getVersion).toList())
                .containsSubsequence("28", "28.1", "29", "30", "30.1", "32", "33");
        assertThat(afterV27.stream().map(MigrationVersion::getVersion).filter("33"::equals).count())
                .isGreaterThanOrEqualTo(1);
        assertThat(afterV27.stream().map(MigrationVersion::getVersion).toList())
                .doesNotContain("31");
    }

    private static List<String> resolvedSqlVersions(String locations) {
        return Arrays.stream(Flyway.configure()
                        .dataSource("jdbc:h2:mem:resolver;DB_CLOSE_DELAY=-1", "sa", "")
                        .locations(locations.split(","))
                        .load()
                        .info()
                        .all())
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion())
                .sorted(Comparator.comparing(MigrationVersion::fromVersion))
                .toList();
    }

    private static List<String> collectSqlFilenames(Path... directories) throws IOException {
        List<String> names = new ArrayList<>();
        for (Path directory : directories) {
            try (var paths = Files.list(directory)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".sql"))
                        .map(path -> path.getFileName().toString())
                        .forEach(names::add);
            }
        }
        return names;
    }

    @Test
    void testProfileShouldNotLoadMysqlBaseline() throws IOException {
        List<String> migrationFiles = collectSqlFilenames(
                Path.of("sql/migrations"),
                Path.of("sql/h2-test"),
                Path.of("src/test/resources/sql/test-migrations"));
        assertThat(migrationFiles).noneMatch(name -> name.startsWith("B33__"));
    }

    private static void assertNoDuplicateMigrationKeys(List<String> migrationFiles, String profile) {
        Set<String> keys = new HashSet<>();
        Set<String> duplicates = migrationFiles.stream()
                .map(FlywayMigrationTopologyTest::parseMigrationKey)
                .filter(key -> !keys.add(key))
                .collect(Collectors.toSet());
        assertThat(duplicates)
                .as("duplicate migration keys in %s profile: %s", profile, duplicates)
                .isEmpty();
    }

    private static String parseMigrationKey(String filename) {
        Matcher matcher = MIGRATION_FILENAME.matcher(filename);
        assertThat(matcher.matches()).as("migration filename %s", filename).isTrue();
        String prefix = matcher.group(1) == null || matcher.group(1).isBlank() ? "V" : matcher.group(1).toUpperCase(Locale.ROOT);
        return prefix + ":" + matcher.group(2).replace('_', '.');
    }

    private static String parseFilenameVersion(String filename) {
        return parseMigrationKey(filename).substring(2);
    }
}
