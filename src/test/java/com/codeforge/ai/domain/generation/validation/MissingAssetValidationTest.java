package com.codeforge.ai.domain.generation.validation;

import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingAssetValidationTest {

    private final GeneratedArtifactValidator validator = new GeneratedArtifactValidator();

    @Test
    void shouldRejectMissingLinkedStylesheet() {
        String html = """
                <!doctype html>
                <html lang="zh-CN">
                <head><link rel="stylesheet" href="style.css"></head>
                <body><main>客户列表</main></body>
                </html>
                """;
        GeneratedProject project = new GeneratedProject(
                "CRM",
                "CRM",
                "WEB_APP",
                "req",
                List.of(new GeneratedProjectFile("index.html", "index.html", html)));

        ArtifactValidationResult result = validator.validate(project, "HTML");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(AiArtifactErrorCodes.ASSET_MISSING);
    }

    @Test
    void shouldAcceptExistingLinkedStylesheet() {
        String html = """
                <!doctype html>
                <html lang="zh-CN">
                <head><link rel="stylesheet" href="style.css"></head>
                <body><main>客户列表</main></body>
                </html>
                """;
        GeneratedProject project = new GeneratedProject(
                "CRM",
                "CRM",
                "WEB_APP",
                "req",
                List.of(
                        new GeneratedProjectFile("index.html", "index.html", html),
                        new GeneratedProjectFile("style.css", "style.css", "body { margin: 0; }")));

        ArtifactValidationResult result = validator.validate(project, "HTML");

        assertThat(result.isValid()).isTrue();
    }
}
