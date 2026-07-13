package com.codeforge.ai.domain.generation.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import org.junit.jupiter.api.Test;

class JsonEncodedFileContentDecoderSecurityTest {

    @Test
    void CompleteNestedHtmlJsonStringDecodesOnceTest() throws Exception {
        String input = "<!DOCTYPE html>\\n<html lang=\\\"en\\\">\\n<body>\\n<p>ok</p>\\n</body>\\n</html>";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("index.html", input);

        assertThat(normalized).startsWith("<!DOCTYPE html>\n");
        assertThat(normalized).contains("<html lang=\"en\">");
        assertThat(JsonEncodedFileContentDecoder.countLiteralBackslashN(normalized)).isZero();
    }

    @Test
    void NormalHtmlWithLiteralBackslashNIsNotDecodedTest() {
        String html = "<!DOCTYPE html>\\n<html>\\n<head><title>x</title></head>\\n<body>\\n</body>\\n</html>";
        String preserved = "const note = \"line1\\nline2\"; // not html start";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("notes.html", preserved);
        assertThat(normalized).isEqualTo(preserved);
    }

    @Test
    void JavascriptWithThreeLegalEscapedNewlinesIsPreservedTest() {
        String javascript = """
                const a = "1\\n2";
                const b = "3\\n4";
                const c = "5\\n6";
                """;
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("app.js", javascript);
        assertThat(normalized).isEqualTo(javascript);
        assertThat(JsonEncodedFileContentDecoder.countLiteralBackslashN(normalized)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void JsonTopLevelStringIsPreservedTest() {
        String json = "\"line1\\nline2\"";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("data.json", json);
        assertThat(normalized).isEqualTo(json);
    }

    @Test
    void JsonObjectWithMultipleEscapedNewlinesIsPreservedTest() {
        String json = "{\"a\":\"1\\n2\",\"b\":\"3\\n4\"}";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("data.json", json);
        assertThat(normalized).isEqualTo(json);
    }

    @Test
    void CssWithMultipleEscapedSequencesIsPreservedTest() {
        String css = ".a::before{content:\"1\\n2\";}\\n.b{color:red;}";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("app.css", css);
        assertThat(normalized).isEqualTo(css);
    }

    @Test
    void AmbiguousJavascriptNestedStringFailsSafeTest() {
        String javascript = "\"line1\\nline2\\nline3\"";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("app.js", javascript);
        assertThat(normalized).isEqualTo(javascript);
    }

    @Test
    void AmbiguousJsonNestedStringFailsSafeTest() {
        String json = "\"a\\nb\\nc\"";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("payload.json", json);
        assertThat(normalized).isEqualTo(json);
    }

    @Test
    void DecoderNeverLoopsTest() {
        String once = "<!DOCTYPE html>\n<html><body>ok</body></html>";
        String first = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("index.html", once);
        String second = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("index.html", first);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void PreviewLayerDoesNotDecodeAgainTest() {
        String decoded = "<!DOCTYPE html>\n<html><body>ok</body></html>";
        String previewPass = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("index.html", decoded);
        assertThat(previewPass).isEqualTo(decoded);
    }

    @Test
    void DecoderErrorDoesNotExposeFullContentTest() {
        String secret = "SECRET_TOKEN_" + "x".repeat(200);
        String malformed = secret + "\\uZZZZ";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("index.html", malformed);
        assertThat(normalized).isEqualTo(malformed);
    }

    @Test
    void MalformedNestedStringReturnsSafeErrorTest() {
        String malformed = "not-a-complete-json-string-literal";
        String normalized = JsonEncodedFileContentDecoder.normalizeGenerationFileContent("index.html", malformed);
        assertThat(normalized).isEqualTo(malformed);
    }

    @Test
    void RepairAmbiguousContentThrowsTest() {
        String javascript = "\"line1\\nline2\\nline3\"";
        assertThatThrownBy(() -> JsonEncodedFileContentDecoder.normalizeRepairFileContent("app.js", javascript))
                .isInstanceOf(AiGenerationFailureException.class)
                .extracting(ex -> ((AiGenerationFailureException) ex).errorCode())
                .isEqualTo(JsonEncodedFileContentDecoder.AMBIGUOUS_DOUBLE_ENCODED_CONTENT);
    }
}
