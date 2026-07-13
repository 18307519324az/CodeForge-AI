package com.codeforge.ai.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptMarkerNonDisclosureGateTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path RESOURCES_ROOT = PROJECT_ROOT.resolve("src/main/resources");

    @Test
    void PromptMarkerIsNotRenderedInDefaultGeneratedPageTest() throws IOException {
        if (!Files.exists(RESOURCES_ROOT)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(RESOURCES_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".sql") || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".md");
                    })
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            assertThat(content)
                                    .as("gate-only marker leaked into default resource " + path)
                                    .doesNotContain("CF_REAL_RUNTIME_SYSTEM_V1_")
                                    .doesNotContain("CF_REAL_RUNTIME_USER_V1_")
                                    .doesNotContain("runtime acceptance");
                        } catch (IOException exception) {
                            throw new AssertionError(exception);
                        }
                    });
        }
    }
}
