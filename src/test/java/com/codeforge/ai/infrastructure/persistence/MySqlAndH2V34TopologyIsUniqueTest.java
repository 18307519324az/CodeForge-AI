package com.codeforge.ai.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlAndH2V34TopologyIsUniqueTest {

    @Test
    void h2V34ExistsWithoutMysqlDuplicate() throws IOException {
        List<String> h2Migrations = Files.list(Path.of("sql/h2-test"))
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("V34__"))
                .toList();
        List<String> mysqlMigrations = Files.walk(Path.of("sql"))
                .filter(Files::isRegularFile)
                .map(path -> path.toString().replace('\\', '/'))
                .filter(path -> path.contains("mysql"))
                .map(path -> Path.of(path).getFileName().toString())
                .filter(name -> name.startsWith("V34__"))
                .toList();

        assertThat(h2Migrations).containsExactly("V34__prompt_template_version_status.sql");
        assertThat(mysqlMigrations).isEmpty();
    }
}
