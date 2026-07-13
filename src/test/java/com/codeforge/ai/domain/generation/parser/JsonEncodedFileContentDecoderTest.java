package com.codeforge.ai.domain.generation.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonEncodedFileContentDecoderTest {

    @Test
    void JsonFileContentDecodesNewlineExactlyOnceTest() throws Exception {
        String doubleEncoded = "<!DOCTYPE html>\\n<html>\\n<head>\\n<title>Demo</title>\\n</head>\\n</html>";
        String decoded = JsonEncodedFileContentDecoder.decodeJsonStringLiteralOnce(doubleEncoded);

        assertThat(decoded).contains("\n");
        assertThat(decoded).doesNotContain("\\n");
        assertThat(JsonEncodedFileContentDecoder.countLiteralBackslashN(decoded)).isZero();
    }

    @Test
    void DoubleEncodedJsonStringIsDecodedAtParserBoundaryTest() {
        String input = "<!DOCTYPE html>\\n<html lang=\\\"en\\\">\\n<body>\\n<p>ok</p>\\n</body>\\n</html>";
        String normalized = JsonEncodedFileContentDecoder.normalizeAfterParse("index.html", input);

        assertThat(normalized).startsWith("<!DOCTYPE html>\n");
        assertThat(normalized).contains("<html lang=\"en\">");
        assertThat(JsonEncodedFileContentDecoder.shouldDecodeNestedJsonString("index.html", input)).isTrue();
        assertThat(JsonEncodedFileContentDecoder.shouldDecodeNestedJsonString("index.html", normalized)).isFalse();
    }

    @Test
    void ValidJavascriptEscapedNewlineIsPreservedTest() {
        String javascript = "const text = \"line1\\nline2\";\nconsole.log(text);";
        String normalized = JsonEncodedFileContentDecoder.normalizeAfterParse("app.js", javascript);

        assertThat(normalized).isEqualTo(javascript);
        assertThat(normalized).contains("\\n");
    }

    @Test
    void ValidJsonEscapedNewlineIsPreservedTest() {
        String json = "{\"message\":\"a\\nb\"}";
        String normalized = JsonEncodedFileContentDecoder.normalizeAfterParse("data.json", json);

        assertThat(normalized).isEqualTo(json);
        assertThat(normalized).contains("\\n");
    }

    @Test
    void ParserDoesNotRepeatedlyUnescapeContentTest() {
        String onceDecoded = "<!DOCTYPE html>\n<html>\n<body>ok</body>\n</html>";
        String normalized = JsonEncodedFileContentDecoder.normalizeAfterParse("index.html", onceDecoded);

        assertThat(normalized).isEqualTo(onceDecoded);
    }

    @Test
    void MalformedNestedJsonReturnsSafeErrorTest() {
        String malformed = "not-a-complete-json-string-literal";
        String normalized = JsonEncodedFileContentDecoder.normalizeAfterParse("index.html", malformed);
        assertThat(normalized).isEqualTo(malformed);
    }
}
