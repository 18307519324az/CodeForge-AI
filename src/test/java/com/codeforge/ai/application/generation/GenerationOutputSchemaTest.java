package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.prompt.GenerationOutputSchema;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationOutputSchemaTest {

    private final PromptResourceLoader loader = new PromptResourceLoader();
    private final AiGeneratedProjectParser parser = new AiGeneratedProjectParser();

    @Test
    void generationSchemaDoesNotRequireDescriptionTest() {
        String prompt = loader.load("codegen-html-system-prompt.txt");

        assertThat(prompt).doesNotContain("\"description\"");
        assertThat(prompt).doesNotContain("\"projectName\"");
        assertThat(prompt).contains("\"files\"");
        assertThat(prompt).contains("\"path\": \"index.html\"");
    }

    @Test
    void generationSchemaDoesNotRequireUnusedMetadataTest() {
        String userPrompt = AiCodegenPromptBuilder.buildUserPrompt(minimalContext());

        assertThat(userPrompt).doesNotContain("\"projectName\"");
        assertThat(userPrompt).doesNotContain("\"description\"");
        assertThat(userPrompt).doesNotContain("At least two statistics or metric cards");
        assertThat(userPrompt).doesNotContain("At least one lightweight chart-like visualization");
    }

    @Test
    void legacyResponseWithDescriptionStillParsesTest() {
        String json = """
                {
                  "projectName": "待办",
                  "description": "ignored metadata",
                  "files": [
                    {
                      "path": "index.html",
                      "content": "<!doctype html><html lang=\\"zh-CN\\"><head><meta charset=\\"UTF-8\\"><title>待办</title></head><body></body></html>"
                    }
                  ]
                }
                """;

        GeneratedProject project = parser.parse(json, "HTML");

        assertThat(project.files()).hasSize(1);
        assertThat(project.summary()).isEqualTo("待办");
    }

    @Test
    void minimalFilesOnlyResponseParsesTest() {
        String json = """
                {"files":[{"path":"index.html","content":"<!doctype html><html lang=\\"zh-CN\\"><head><meta charset=\\"UTF-8\\"><title>待办</title></head><body></body></html>"}]}
                """;

        GeneratedProject project = parser.parse(json, "HTML");

        assertThat(project.files()).hasSize(1);
        assertThat(project.summary()).isEqualTo("AI 生成");
    }

    @Test
    void applicationNameProvidesSummaryWithoutModelProjectNameTest() {
        GeneratedProject parsed = parser.parse(
                "{\"files\":[{\"path\":\"index.html\",\"content\":\"<!doctype html><html lang=\\\"zh-CN\\\"><head><meta charset=\\\"UTF-8\\\"><title>今日任务</title></head><body></body></html>\"}]}",
                "HTML");

        GeneratedProject withAppName = new GeneratedProject(
                parsed.summary(),
                "今日任务",
                parsed.appType(),
                parsed.requirement(),
                parsed.files());

        assertThat(withAppName.appName()).isEqualTo("今日任务");
        assertThat(withAppName.summary()).isEqualTo("AI 生成");
    }

    private GenerationContext minimalContext() {
        return new GenerationContext(
                "生成一个极简待办清单页面，只要 index.html",
                "今日任务",
                "WEB_APP",
                "HTML",
                1L, 2L, 3L,
                null, null, null, null, null, null);
    }
}
