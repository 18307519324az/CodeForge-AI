package com.codeforge.ai.domain.generation.parser;

import com.codeforge.ai.domain.generation.GeneratedProject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedProjectParserTest {

    private final AiGeneratedProjectParser parser = new AiGeneratedProjectParser();

    @Test
    void shouldParseJsonProject() {
        String json = """
                {
                  "summary": "待办清单",
                  "files": [
                    {
                      "filePath": "index.html",
                      "fileName": "index.html",
                      "fileContent": "<!doctype html><html><head><meta charset=\\"UTF-8\\"><title>今日任务</title></head><body></body></html>"
                    }
                  ]
                }
                """;

        GeneratedProject project = parser.parse(json, "JSON");

        assertThat(project.files()).hasSize(1);
        assertThat(project.files().getFirst().filePath()).isEqualTo("index.html");
        assertThat(project.files().getFirst().content()).contains("今日任务");
    }

    @Test
    void shouldParseHtmlCodeBlock() {
        String raw = """
                ```html
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="UTF-8"><title>工单系统</title></head><body></body></html>
                ```
                """;

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.files()).hasSize(1);
        assertThat(project.files().getFirst().content()).contains("工单系统");
    }

    @Test
    void shouldPreferJsonForMultiFileWhenPromptReturnsJson() {
        String json = """
                {
                  "summary": "电商后台",
                  "files": [
                    {"filePath": "index.html", "fileName": "index.html", "fileContent": "<!doctype html><html><head><meta charset=\\"UTF-8\\"><title>电商</title></head><body></body></html>"},
                    {"filePath": "style.css", "fileName": "style.css", "fileContent": "body{margin:0}"},
                    {"filePath": "script.js", "fileName": "script.js", "fileContent": "console.log('ok')"}
                  ]
                }
                """;

        GeneratedProject project = parser.parse(json, "MULTI_FILE");

        assertThat(project.files()).hasSize(3);
        assertThat(project.files().getFirst().content()).contains("电商");
    }

    @Test
    void shouldFailWhenJsonHasNoFiles() {
        assertThatThrownBy(() -> parser.parse("{\"summary\":\"x\",\"files\":[]}", "HTML"))
                .isInstanceOf(AiGeneratedProjectParser.AiOutputParseException.class);
    }
}
