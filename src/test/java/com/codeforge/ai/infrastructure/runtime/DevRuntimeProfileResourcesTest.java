package com.codeforge.ai.infrastructure.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DevRuntimeProfileResourcesTest {

    @Test
    void localProfileShouldDisableFlywayUntilHistoryCatchup() throws Exception {
        String localYaml = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(localYaml).contains("enabled: false");
        assertThat(localYaml).contains("scripts/mysql-flyway-catchup.sql");
    }

    @Test
    void devProfileShouldRemainExplicitH2FileMode() throws Exception {
        String devYaml = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(devYaml).contains("jdbc:h2:file:./.local-data/codeforge-dev");
        assertThat(devYaml).contains("dev-application-seed.sql");
    }

    @Test
    void devStartScriptShouldDefaultToLocalProfile() throws Exception {
        String script = Files.readString(Path.of("scripts/dev-start.ps1"));

        assertThat(script).contains("[string]$Profile = 'local'");
        assertThat(script).contains("ValidateSet('local', 'dev-h2')");
        assertThat(script).contains("-Dspring-boot.run.profiles=local");
        assertThat(script).contains("-Dspring-boot.run.profiles=dev");
    }
}
