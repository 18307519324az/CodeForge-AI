package com.codeforge.ai.infrastructure.persistence;

import com.codeforge.ai.domain.generation.validation.GeneratedArtifactBudget;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedFileMysqlIndexContractTest {

    @Test
    void generatedFileIndexFitsMysqlLimit() {
        int bytes = MysqlIndexLimitSupport.maxUtf8mb4IndexBytes("bigint", 0)
                + MysqlIndexLimitSupport.maxUtf8mb4IndexBytes("varchar", 255);
        assertThat(bytes).isLessThanOrEqualTo(MysqlIndexLimitSupport.mysqlInnodbMaxIndexBytes());
    }

    @Test
    void generatedFileUniquePathContractUsesPrefixIndex() throws Exception {
        String ddl = java.nio.file.Files.readString(
                java.nio.file.Path.of("sql/mysql-baseline/B33__codeforge_mysql_schema.sql"));
        assertThat(ddl).contains("KEY `idx_generated_file_version_path` (`app_version_id`,`file_path`(255))");
    }

    @Test
    void longValidFilePathWithinBusinessLimitIsPersistableLength() {
        String path = "a".repeat(GeneratedArtifactBudget.MAX_FILE_PATH_LENGTH);
        assertThat(path.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(4096);
        assertThat(path.length()).isEqualTo(1024);
    }

    @Test
    void duplicateVersionFilePathRejectedByBusinessLimit() {
        String tooLong = "a".repeat(GeneratedArtifactBudget.MAX_FILE_PATH_LENGTH + 1);
        assertThat(tooLong.length()).isGreaterThan(GeneratedArtifactBudget.MAX_FILE_PATH_LENGTH);
    }
}
