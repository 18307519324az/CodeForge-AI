package com.codeforge.ai.domain.generation.validation;

import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedArtifactIntegrityTest {

    private final GeneratedArtifactValidator validator = new GeneratedArtifactValidator();

    private static String validHtml() {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"><title>CRM</title></head>
                <body><main>客户列表</main></body>
                </html>
                """;
    }

    @Test
    void shouldAcceptValidSingleFileHtml() {
        GeneratedProject project = new GeneratedProject(
                "CRM",
                "CRM",
                "WEB_APP",
                "req",
                List.of(new GeneratedProjectFile("index.html", "index.html", validHtml())));

        ArtifactValidationResult result = validator.validate(project, "HTML");

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectBareNewlineCorruption() {
        String corrupted = "<!DOCTYPE html>n<html lang=\"zh-CN\"><head></head><body>n<div>客户列表</div></body></html>";
        GeneratedProject project = new GeneratedProject(
                "CRM",
                "CRM",
                "WEB_APP",
                "req",
                List.of(new GeneratedProjectFile("index.html", "index.html", corrupted)));

        ArtifactValidationResult result = validator.validate(project, "HTML");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(AiArtifactErrorCodes.ESCAPE_CORRUPTED);
    }
}
