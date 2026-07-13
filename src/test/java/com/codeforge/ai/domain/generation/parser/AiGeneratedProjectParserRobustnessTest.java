package com.codeforge.ai.domain.generation.parser;

import com.codeforge.ai.domain.generation.GeneratedProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGeneratedProjectParserRobustnessTest {

    private final AiGeneratedProjectParser parser = new AiGeneratedProjectParser();

    private static String completeHtml(String title) {
        return "<!doctype html><html lang='zh-CN'><head><meta charset='UTF-8'><title>"
                + title + "</title></head><body><main>" + title + "</main></body></html>";
    }

    private static String jsonContent(String title) {
        return completeHtml(title).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Test
    void shouldParseFencedJsonWithTrailingComma() {
        String raw = """
                ```json
                {
                  "projectName": "待办清单",
                  "files": [
                    {
                      "path": "index.html",
                      "content": "%s",
                    }
                  ],
                }
                """.formatted(jsonContent("今日任务"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.files()).hasSize(1);
        assertThat(project.files().getFirst().content()).contains("今日任务");
    }

    @Test
    void shouldParseJsonWrappedByExplanationTextBefore() {
        String raw = """
                下面是生成结果，请直接落库：
                {"projectName":"客户管理","files":[{"path":"index.html","content":"%s"}]}
                """.formatted(jsonContent("客户管理"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("客户管理");
    }

    @Test
    void shouldParseJsonWrappedByExplanationTextAfter() {
        String raw = """
                {"projectName":"工单","files":[{"path":"index.html","content":"%s"}]}
                以上就是结果。
                """.formatted(jsonContent("工单"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.files().getFirst().content()).contains("工单");
    }

    @Test
    void shouldParseMarkdownHeaderBeforeJson() {
        String raw = """
                ### Result
                {"projectName":"商城","files":[{"path":"index.html","content":"%s"}]}
                """.formatted(jsonContent("商城"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("商城");
    }

    @Test
    void shouldParseMultipleFencesAndPickFirstValid() {
        String raw = """
                ```json
                {"projectName":"bad","files":[]}
                ```
                ```json
                {"projectName":"好","files":[{"path":"index.html","content":"%s"}]}
                ```
                """.formatted(jsonContent("好"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("好");
    }

    @Test
    void shouldParseSecondJsonWhenFirstIsInvalidEmptyFiles() {
        String raw = """
                {"projectName":"empty","files":[]}
                {"projectName":"有效","files":[{"path":"index.html","content":"%s"}]}
                """.formatted(jsonContent("有效"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("有效");
    }

    @Test
    void shouldParseRawHtmlWithoutFenceWhenStandalone() {
        String raw = completeHtml("工单系统");

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.files()).hasSize(1);
        assertThat(project.files().getFirst().content()).contains("工单系统");
    }

    @Test
    void shouldParseHtmlAfterShortPreamble() {
        String raw = "好的\n" + completeHtml("待办");

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.files().getFirst().content()).contains("待办");
    }

    @Test
    void shouldParseMultiCodeBlocks() {
        String raw = """
                ```html
                %s
                ```
                ```css
                body { margin: 0; }
                ```
                ```js
                console.log('mall');
                ```
                """.formatted(jsonContent("商城后台"));

        GeneratedProject project = parser.parse(raw, "MULTI_FILE");

        assertThat(project.files()).hasSize(3);
    }

    @Test
    void shouldParseSingleQuotedKeys() {
        String raw = """
                {'projectName':'待办','files':[{'path':'index.html','content':"%s"}]}
                """.formatted(jsonContent("待办"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("待办");
    }

    @Test
    void shouldParseLegacyFileContentField() {
        String raw = """
                {"summary":"CRM","files":[{"filePath":"index.html","fileName":"index.html","fileContent":"%s"}]}
                """.formatted(jsonContent("CRM"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.files().getFirst().content()).contains("CRM");
    }

    @Test
    void shouldParseUnicodeContent() {
        String raw = """
                {"projectName":"待办🎯","files":[{"path":"index.html","content":"%s"}]}
                """.formatted(jsonContent("待办🎯"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).contains("待办");
    }

    @Test
    void shouldRejectTruncatedJson() {
        assertThatThrownBy(() -> parser.parse("""
                {"projectName":"待办","files":[{"path":"index.html","content":"<html><body>待办"
                """, "HTML"))
                .isInstanceOf(AiGeneratedProjectParser.AiOutputParseException.class)
                .hasMessageContaining("截断");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "好的，我来生成页面。",
            "Sure! Here's your code",
            "抱歉，无法完成",
            "当然可以，这是结果"
    })
    void shouldRejectNaturalLanguageOnly(String raw) {
        assertThatThrownBy(() -> parser.parse(raw, "HTML"))
                .isInstanceOf(AiGeneratedProjectParser.AiOutputParseException.class);
    }

    @Test
    void shouldRejectHtmlWithLongPreamble() {
        String raw = "这是一段很长的解释文字".repeat(20) + completeHtml("x");

        assertThatThrownBy(() -> parser.parse(raw, "HTML"))
                .isInstanceOf(AiGeneratedProjectParser.AiOutputParseException.class);
    }

    @Test
    void shouldParsePlainFenceWithoutJsonLabel() {
        String raw = """
                ```
                {"projectName":"工单","files":[{"path":"index.html","content":"%s"}]}
                ```
                """.formatted(jsonContent("工单"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("工单");
    }

    @Test
    void shouldParseJsonWithExtraWhitespace() {
        String raw = """
                
                
                {"projectName":"  商城  ","files":[{"path":"index.html","content":"%s"}]}
                
                """.formatted(jsonContent("商城"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).contains("商城");
    }

    @Test
    void shouldParseFileBlockFormat() {
        String raw = """
                FILE:index.html
                %s
                ---END---
                """.formatted(completeHtml("FILE"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.files()).hasSize(1);
    }

    @Test
    void shouldRejectEmptyFilesArray() {
        assertThatThrownBy(() -> parser.parse("{\"projectName\":\"x\",\"files\":[]}", "HTML"))
                .isInstanceOf(AiGeneratedProjectParser.AiOutputParseException.class)
                .hasMessageContaining("files 为空");
    }

    @Test
    void shouldRejectIncompleteHtmlInJson() {
        assertThatThrownBy(() -> parser.parse(
                "{\"projectName\":\"x\",\"files\":[{\"path\":\"index.html\",\"content\":\"<html>\"}]}",
                "HTML"))
                .isInstanceOf(AiGeneratedProjectParser.AiOutputParseException.class);
    }

    @Test
    void shouldParseJsonEmbeddedInMixedText() {
        String raw = """
                分析完毕，输出如下：
                {"projectName":"电商","description":"后台","files":[{"path":"index.html","content":"%s"}]}
                请查收。
                """.formatted(jsonContent("电商"));

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("电商");
    }

    @Test
    void shouldRepairUnescapedQuotesInsideHtmlContent() {
        String raw = """
                {"projectName":"待办","description":"d","files":[{"path":"index.html","content":"<!doctype html><html><head><meta charset="UTF-8"><title>待办</title></head><body><script>const s = String(1);</script></body></html>"}]}
                """;

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("待办");
        assertThat(project.files().getFirst().content()).contains("charset=");
    }

    @Test
    void shouldParseJsonWithCssBracesInHtmlContent() {
        String raw = """
                {"projectName":"商城","files":[{"path":"index.html","content":"<!doctype html><html><head><meta charset="UTF-8"><style>body { margin: 0; } .main { padding: 8px; }</style></head><body><main>商城</main></body></html>"}]}
                """;

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("商城");
        assertThat(project.files().getFirst().content()).contains("margin");
    }

    @Test
    void shouldRepairMultilineHtmlInsideJsonString() {
        String raw = """
                {"projectName":"商城","files":[{"path":"index.html","content":"<!doctype html>
                <html><head><meta charset='UTF-8'><title>商城</title></head><body></body></html>"}]}
                """;

        GeneratedProject project = parser.parse(raw, "HTML");

        assertThat(project.summary()).isEqualTo("商城");
    }
}
