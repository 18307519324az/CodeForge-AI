package com.codeforge.ai.domain.generation.parser;

import com.codeforge.ai.domain.generation.GeneratedProject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewlineEscapeIntegrityTest {

    private final AiGeneratedProjectParser parser = new AiGeneratedProjectParser();

    @Test
    void shouldUnescapeNewlinesWhenRecoveringMalformedJson() {
        String html = """
                <!doctype html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"><title>CRM</title></head>
                <body><main>客户列表</main></body>
                </html>
                """;
        String escaped = html.replace("\n", "\\n").replace("\"", "\\\"");
        String raw = """
                下面是结果：
                {"projectName":"CRM","files":[{"path":"index.html","content":"%s"}]}
                """.formatted(escaped);

        GeneratedProject project = parser.parse(raw, "HTML");
        String content = project.files().getFirst().content();

        assertThat(content).contains("\n");
        assertThat(content).doesNotContain("<!doctype html>n<html");
        assertThat(content).contains("<main>客户列表</main>");
    }
}
