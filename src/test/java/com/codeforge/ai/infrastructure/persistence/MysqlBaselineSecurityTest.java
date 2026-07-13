package com.codeforge.ai.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlBaselineSecurityTest {

    private static final Path BASELINE = Path.of("sql/mysql-baseline/B33__codeforge_mysql_schema.sql");
    private static final List<String> ALLOWLIST_INSERT_PREFIXES = List.of(
            "INSERT INTO ai_routing_config",
            "INSERT INTO model_provider");

    @Test
    void mysqlBaselineContainsNoCreateDatabase() throws IOException {
        String sql = readBaseline();
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("CREATE DATABASE");
    }

    @Test
    void mysqlBaselineContainsNoUseStatement() throws IOException {
        String sql = readBaseline();
        assertThat(sql).doesNotContainPattern("(?im)^\\s*USE\\s+");
    }

    @Test
    void mysqlBaselineContainsNoDefiner() throws IOException {
        String sql = readBaseline();
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("DEFINER");
    }

    @Test
    void mysqlBaselineContainsNoGrantOrUserCreation() throws IOException {
        String sql = readBaseline();
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("GRANT ");
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("CREATE USER");
    }

    @Test
    void mysqlBaselineContainsNoFlywayHistoryTable() throws IOException {
        String sql = readBaseline();
        assertThat(sql).doesNotContain("flyway_schema_history");
    }

    @Test
    void mysqlBaselineContainsNoBusinessUserData() throws IOException {
        String sql = readBaseline();
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("INSERT INTO USER");
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("INSERT INTO WORKSPACE");
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("INSERT INTO AI_APP");
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("INSERT INTO GENERATION_TASK");
    }

    @Test
    void mysqlBaselineContainsNoApiKeyOrToken() throws IOException {
        String inserts = readInsertStatements();
        assertThat(inserts.toLowerCase(Locale.ROOT)).doesNotContain("sk-");
        assertThat(inserts.toLowerCase(Locale.ROOT)).doesNotContain("bearer ");
        assertThat(readBaseline()).doesNotContain("INSERT INTO model_provider_credential");
    }

    @Test
    void mysqlBaselineContainsNoPasswordHash() throws IOException {
        String inserts = readInsertStatements();
        assertThat(inserts.toLowerCase(Locale.ROOT)).doesNotContain("admin123");
        assertThat(inserts).doesNotContain("$2a$");
        assertThat(inserts.toLowerCase(Locale.ROOT)).doesNotContain("insert into user");
    }

    @Test
    void mysqlBaselineContainsNoAbsolutePath() throws IOException {
        String sql = readBaseline();
        assertThat(sql).doesNotContain("D:\\\\");
        assertThat(sql).doesNotContain("C:\\\\");
        assertThat(sql).doesNotContain("/Users/");
    }

    @Test
    void mysqlBaselineContainsNoAutoIncrementRuntimeCounter() throws IOException {
        String sql = readBaseline();
        assertThat(sql).doesNotContainPattern("AUTO_INCREMENT=\\d+");
    }

    @Test
    void mysqlBaselineSeedDataMatchesAllowlist() throws IOException {
        List<String> inserts = readBaseline().lines()
                .map(String::trim)
                .filter(line -> line.regionMatches(true, 0, "INSERT INTO", 0, "INSERT INTO".length()))
                .toList();
        assertThat(inserts).isNotEmpty();
        assertThat(inserts).allMatch(line -> ALLOWLIST_INSERT_PREFIXES.stream().anyMatch(line::startsWith));
    }

    @Test
    void mysqlBaselineAllIndexesFitMysqlLimit() throws IOException {
        String generatedFileDdl = extractGeneratedFileDdl(readBaseline());
        assertThat(generatedFileDdl).contains("file_path`(255)");
        int bytes = MysqlIndexLimitSupport.maxUtf8mb4IndexBytes("bigint", 0)
                + MysqlIndexLimitSupport.maxUtf8mb4IndexBytes("varchar", 255);
        assertThat(bytes).isLessThanOrEqualTo(MysqlIndexLimitSupport.mysqlInnodbMaxIndexBytes());
    }

    @Test
    void mysqlBaselineTableInventoryMatchesCanonicalV33() throws IOException {
        String sql = readBaseline().toLowerCase(Locale.ROOT);
        List<String> requiredTables = List.of(
                "user", "workspace", "ai_app", "app_version", "generated_file", "generation_task",
                "model_call_log", "model_provider", "model_provider_credential", "ai_routing_config",
                "prompt_template", "prompt_template_version", "app_publication", "publication_view_dedupe");
        requiredTables.forEach(table -> assertThat(sql).contains("create table if not exists `" + table + "`"));
        assertThat(sql).doesNotContain("backup_before_utf8_fix");
    }

    private static String extractGeneratedFileDdl(String sql) {
        Pattern pattern = Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS `generated_file`.*?;\\s*");
        Matcher matcher = pattern.matcher(sql);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private static String readBaseline() throws IOException {
        assertThat(BASELINE).exists();
        return Files.readString(BASELINE);
    }

    private static String readInsertStatements() throws IOException {
        return String.join("\n", readBaseline().lines()
                .map(String::trim)
                .filter(line -> line.regionMatches(true, 0, "INSERT INTO", 0, "INSERT INTO".length())
                        || line.regionMatches(true, 0, "SELECT", 0, "SELECT".length())
                        || line.regionMatches(true, 0, "WHERE NOT EXISTS", 0, "WHERE NOT EXISTS".length()))
                .toList());
    }
}
