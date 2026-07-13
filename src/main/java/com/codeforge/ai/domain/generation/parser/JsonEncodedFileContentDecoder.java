package com.codeforge.ai.domain.generation.parser;

import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;

/**
 * Decodes file content only when the entire value is a complete nested JSON string literal.
 * Never uses newline-count heuristics or global replace.
 */
public final class JsonEncodedFileContentDecoder {

    public static final String AMBIGUOUS_DOUBLE_ENCODED_CONTENT = "AMBIGUOUS_DOUBLE_ENCODED_CONTENT";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonEncodedFileContentDecoder() {
    }

    public static String normalizeGenerationFileContent(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        DecodeAttempt attempt = tryDecodeNestedJsonStringOnce(content);
        if (!attempt.changed()) {
            return content;
        }
        if (isHtmlArtifact(filePath) && verifyHtmlStart(attempt.decoded())) {
            return attempt.decoded();
        }
        if (isSvgArtifact(filePath) && verifySvgStart(attempt.decoded())) {
            return attempt.decoded();
        }
        return content;
    }

    public static String normalizeRepairFileContent(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        DecodeAttempt attempt = tryDecodeNestedJsonStringOnce(content);
        if (!attempt.changed()) {
            return content;
        }
        if (isHtmlArtifact(filePath) && verifyHtmlStart(attempt.decoded())) {
            return attempt.decoded();
        }
        if (isSvgArtifact(filePath) && verifySvgStart(attempt.decoded())) {
            return attempt.decoded();
        }
        throw new AiGenerationFailureException(
                AMBIGUOUS_DOUBLE_ENCODED_CONTENT,
                "无法安全解码修复内容",
                "Cannot safely decode repair content for " + filePath);
    }

    /**
     * @deprecated Use {@link #normalizeGenerationFileContent} or {@link #normalizeRepairFileContent}.
     */
    @Deprecated
    public static String normalizeAfterParse(String filePath, String content) {
        return normalizeGenerationFileContent(filePath, content);
    }

    public static boolean shouldDecodeNestedJsonString(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        DecodeAttempt attempt = tryDecodeNestedJsonStringOnce(content);
        if (!attempt.changed()) {
            return false;
        }
        return isHtmlArtifact(filePath) && verifyHtmlStart(attempt.decoded());
    }

    static DecodeAttempt tryDecodeNestedJsonStringOnce(String content) {
        try {
            String decoded = decodeJsonStringLiteralOnce(content);
            boolean changed = !content.equals(decoded);
            return new DecodeAttempt(decoded, changed);
        } catch (JsonProcessingException exception) {
            return new DecodeAttempt(content, false);
        }
    }

    static String decodeJsonStringLiteralOnce(String content) throws JsonProcessingException {
        StringBuilder json = new StringBuilder(content.length() + 16);
        json.append('"');
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '\\' && index + 1 < content.length()) {
                char next = content.charAt(index + 1);
                if (isJsonEscapeLead(next)) {
                    json.append('\\').append(next);
                    index++;
                    continue;
                }
            }
            if (current == '"') {
                json.append("\\\"");
            } else if (current == '\\') {
                json.append("\\\\");
            } else if (current < 0x20) {
                json.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
            } else {
                json.append(current);
            }
        }
        json.append('"');
        return MAPPER.readValue(json.toString(), String.class);
    }

    static int countLiteralBackslashN(String content) {
        int count = 0;
        for (int index = 0; index < content.length() - 1; index++) {
            if (content.charAt(index) == '\\' && content.charAt(index + 1) == 'n') {
                count++;
            }
        }
        return count;
    }

    static boolean verifyHtmlStart(String content) {
        if (content == null) {
            return false;
        }
        String trimmed = content.stripLeading();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("<!doctype html") || lower.startsWith("<html");
    }

    static boolean verifySvgStart(String content) {
        if (content == null) {
            return false;
        }
        return content.stripLeading().toLowerCase(Locale.ROOT).startsWith("<svg");
    }

    private static boolean isHtmlArtifact(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String lower = filePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".htm") || lower.contains("index");
    }

    private static boolean isSvgArtifact(String filePath) {
        return filePath != null && filePath.toLowerCase(Locale.ROOT).endsWith(".svg");
    }

    private static boolean isJsonEscapeLead(char value) {
        return value == 'n' || value == 'r' || value == 't' || value == 'b' || value == 'f'
                || value == '"' || value == '\\' || value == '/' || value == 'u';
    }

    record DecodeAttempt(String decoded, boolean changed) {
    }
}
