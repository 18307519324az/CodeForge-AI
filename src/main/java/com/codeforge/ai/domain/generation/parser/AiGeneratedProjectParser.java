package com.codeforge.ai.domain.generation.parser;

import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiGeneratedProjectParser {
    private static final Logger log = LoggerFactory.getLogger(AiGeneratedProjectParser.class);

    private static final Pattern FILE_BLOCK_PATTERN =
            Pattern.compile("(?ms)^FILE:(.+?)\\R(.*?)\\R---END---\\R?");
    private static final Pattern JSON_FENCE_PATTERN =
            Pattern.compile("(?is)```(?:json)?\\s*(.*?)```");
    private static final Pattern HTML_BLOCK_PATTERN =
            Pattern.compile("(?is)```html\\s*(.*?)```");
    private static final Pattern CSS_BLOCK_PATTERN =
            Pattern.compile("(?is)```css\\s*(.*?)```");
    private static final Pattern JS_BLOCK_PATTERN =
            Pattern.compile("(?is)```(?:javascript|js)\\s*(.*?)```");
    private static final Pattern TRAILING_COMMA_PATTERN =
            Pattern.compile(",\\s*([}\\]])");
    private static final Pattern SINGLE_QUOTED_KEY_PATTERN =
            Pattern.compile("'([^'\\\\]+)'\\s*:");
    private static final Pattern SINGLE_QUOTED_VALUE_PATTERN =
            Pattern.compile(":\\s*'((?:\\\\.|[^'\\\\])*)'");
    private static final Pattern NATURAL_LANGUAGE_PREFIX =
            Pattern.compile("^(?:好的|下面|以下是|Sure!|Here'?s|当然可以|抱歉|下面开始)[^<\\{]*", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper mapper = new ObjectMapper();

    public GeneratedProject parse(String aiContent, String codeGenType) {
        if (aiContent == null || aiContent.isBlank()) {
            throw new AiOutputParseException("AI 输出内容为空");
        }

        String normalized = normalizeWhitespace(aiContent);

        GeneratedProject fileBlockProject = parseFileBlocks(normalized, codeGenType);
        if (fileBlockProject != null) {
            log.info("Parser: detected FILE block format");
            return fileBlockProject;
        }

        AiOutputParseException lastJsonError = null;
        for (String jsonCandidate : collectJsonCandidates(normalized)) {
            try {
                GeneratedProject project = parseJsonProject(jsonCandidate, codeGenType);
                log.info("Parser: recovered valid JSON project");
                return project;
            } catch (AiOutputParseException exception) {
                lastJsonError = exception;
                log.debug("Parser: JSON candidate rejected: {}", exception.getMessage());
            }
        }

        try {
            if (shouldAttemptFullRecovery(normalized)) {
                GeneratedProject recovered = recoverProjectFromMalformedJson(
                        sanitizeJsonStringLiterals(normalized), codeGenType);
                log.info("Parser: recovered malformed JSON project from full output");
                return recovered;
            }
        } catch (AiOutputParseException recoveryError) {
            lastJsonError = recoveryError;
            log.debug("Parser: full-output recovery rejected: {}", recoveryError.getMessage());
        }

        if ("HTML".equalsIgnoreCase(codeGenType) && hasHtmlCodeBlocks(normalized)) {
            log.info("Parser: detected fenced HTML block");
            return parseSingleHtmlProject(normalized, codeGenType);
        }
        if ("HTML".equalsIgnoreCase(codeGenType) && isStandaloneHtmlDocument(normalized)) {
            log.info("Parser: detected standalone raw HTML document");
            return parseSingleHtmlProject(normalized, codeGenType);
        }
        if ("MULTI_FILE".equalsIgnoreCase(codeGenType) && hasHtmlCodeBlocks(normalized)) {
            log.info("Parser: detected multi-file code blocks");
            return parseMultiFileProject(normalized, codeGenType);
        }

        if (lastJsonError != null) {
            log.warn("Fallback reason: {}", lastJsonError.getMessage());
            throw lastJsonError;
        }
        throw new AiOutputParseException("AI 输出未匹配到可解析的项目结构");
    }

    private GeneratedProject parseSingleHtmlProject(String raw, String codeGenType) {
        String html = extractHtmlDocument(raw);
        ensureCompleteHtml(html);
        List<GeneratedProjectFile> files = List.of(
                new GeneratedProjectFile("index.html", "index.html", html.trim())
        );
        return new GeneratedProject("AI 生成 HTML 页面", "AI Generated", codeGenType, "HTML prototype", files);
    }

    private GeneratedProject parseMultiFileProject(String raw, String codeGenType) {
        String html = extractHtmlDocument(raw);
        String css = extractRequiredBlock(CSS_BLOCK_PATTERN, raw, "css");
        String javascript = extractRequiredBlock(JS_BLOCK_PATTERN, raw, "javascript");
        ensureCompleteHtml(html);

        List<GeneratedProjectFile> files = List.of(
                new GeneratedProjectFile("index.html", "index.html", html.trim()),
                new GeneratedProjectFile("style.css", "style.css", css.trim()),
                new GeneratedProjectFile("script.js", "script.js", javascript.trim())
        );
        return new GeneratedProject("AI 生成多文件页面", "AI Generated", codeGenType, "Multi file prototype", files);
    }

    private GeneratedProject parseJsonProject(String raw, String codeGenType) {
        String json = repairJson(extractJson(raw));
        try {
            JsonNode root = mapper.readTree(json);
            String summary = readSummary(root);
            JsonNode filesNode = root.get("files");
            if (filesNode == null || !filesNode.isArray() || filesNode.isEmpty()) {
                throw new AiOutputParseException("AI 输出中的 files 为空");
            }

            List<GeneratedProjectFile> files = new ArrayList<>();
            for (JsonNode fileNode : filesNode) {
                String filePath = readFilePath(fileNode);
                String fileName = readFileName(fileNode, filePath);
                String fileContent = readFileContent(fileNode);
                if (filePath == null || filePath.isBlank() || fileContent == null || fileContent.isBlank()) {
                    continue;
                }
                if ("index.html".equalsIgnoreCase(fileName)) {
                    ensureCompleteHtml(fileContent);
                }
                files.add(new GeneratedProjectFile(filePath, fileName, fileContent));
            }
            if (files.isEmpty()) {
                throw new AiOutputParseException("AI 输出中没有有效文件");
            }
            return new GeneratedProject(summary, "AI Generated", codeGenType, summary, files);
        } catch (AiOutputParseException exception) {
            throw exception;
        } catch (Exception exception) {
            try {
                GeneratedProject recovered = recoverProjectFromMalformedJson(repairJson(raw), codeGenType);
                log.info("Parser: recovered malformed JSON project");
                return recovered;
            } catch (AiOutputParseException recoveryError) {
                throw recoveryError;
            } catch (Exception recoveryError) {
                throw new AiOutputParseException("AI 输出解析失败: " + exception.getMessage());
            }
        }
    }

    private GeneratedProject recoverProjectFromMalformedJson(String json, String codeGenType) {
        String sanitized = sanitizeJsonStringLiterals(json);
        String summary = firstNonBlank(
                extractJsonStringValue(sanitized, "projectName"),
                extractJsonStringValue(sanitized, "summary"),
                "AI 生成");
        String html = firstNonBlank(
                extractJsonStringValue(sanitized, "content"),
                extractJsonStringValue(sanitized, "fileContent"));
        if (html == null || html.isBlank()) {
            html = extractPartialHtmlDocument(sanitized);
        }
        if (html == null || html.isBlank()) {
            throw new AiOutputParseException("AI 输出中没有有效 HTML 内容");
        }
        html = JsonEncodedFileContentDecoder.normalizeAfterParse("index.html", html);
        ensureCompleteHtml(html);
        List<GeneratedProjectFile> files = List.of(
                new GeneratedProjectFile("index.html", "index.html", html.trim()));
        return new GeneratedProject(summary, "AI Generated", codeGenType, summary, files);
    }

    private boolean shouldAttemptFullRecovery(String raw) {
        return raw.contains("\"content\"")
                || raw.contains("\"fileContent\"")
                || (raw.contains("\"files\"") && indexOfHtmlDocument(raw) >= 0);
    }

    private String extractPartialHtmlDocument(String raw) {
        int htmlStart = indexOfHtmlDocument(raw);
        if (htmlStart < 0) {
            return null;
        }
        String tail = raw.substring(htmlStart);
        int htmlEnd = tail.toLowerCase().indexOf("</html>");
        if (htmlEnd < 0) {
            throw new AiOutputParseException("AI 输出中的 HTML 疑似被截断");
        }
        return tail.substring(0, htmlEnd + "</html>".length());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String extractJsonStringValue(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        int start = matcher.end();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaped) {
                if (current == 'u' && i + 4 < json.length()) {
                    String hex = json.substring(i + 1, i + 5);
                    try {
                        value.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException exception) {
                        throw new AiOutputParseException("AI 输出中的 JSON Unicode 转义无效: \\u" + hex);
                    }
                    i += 4;
                } else {
                    appendJsonEscape(value, current);
                }
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                if (isJsonStringClosingQuote(json, i)) {
                    return value.toString();
                }
                value.append(current);
                continue;
            }
            value.append(current);
        }
        throw new AiOutputParseException("AI 输出中的 JSON 疑似被截断");
    }

    private void appendJsonEscape(StringBuilder value, char current) {
        switch (current) {
            case 'n' -> value.append('\n');
            case 'r' -> value.append('\r');
            case 't' -> value.append('\t');
            case 'b' -> value.append('\b');
            case 'f' -> value.append('\f');
            case '"' -> value.append('"');
            case '\\' -> value.append('\\');
            case '/' -> value.append('/');
            default -> value.append(current);
        }
    }

    private String readSummary(JsonNode root) {
        if (root.hasNonNull("projectName")) {
            return root.get("projectName").asText();
        }
        if (root.hasNonNull("summary")) {
            return root.get("summary").asText();
        }
        return "AI 生成";
    }

    private List<String> collectJsonCandidates(String raw) {
        Set<String> candidates = new LinkedHashSet<>();
        Matcher fenceMatcher = JSON_FENCE_PATTERN.matcher(raw);
        while (fenceMatcher.find()) {
            String fenced = fenceMatcher.group(1);
            if (fenced != null && !fenced.isBlank()) {
                candidates.add(sanitizeJsonStringLiterals(fenced.trim()));
                log.debug("Parser: detected fenced json block");
            }
        }

        String stripped = stripMarkdownHeaders(raw);
        stripped = NATURAL_LANGUAGE_PREFIX.matcher(stripped.trim()).replaceFirst("").trim();
        stripped = sanitizeJsonStringLiterals(stripped);

        int searchFrom = 0;
        while (searchFrom < stripped.length()) {
            int start = stripped.indexOf('{', searchFrom);
            if (start < 0) {
                break;
            }
            int end = findMatchingBraceEnd(stripped, start);
            if (end < 0) {
                String remainder = stripped.substring(start).trim();
                if (!remainder.isBlank()) {
                    candidates.add(remainder);
                }
                break;
            }
            candidates.add(stripped.substring(start, end + 1));
            searchFrom = end + 1;
        }
        return new ArrayList<>(candidates);
    }

    private String stripMarkdownHeaders(String raw) {
        return raw.replaceAll("(?m)^#{1,6}\\s+.*\\R", "").trim();
    }

    private String extractRequiredBlock(Pattern pattern, String raw, String language) {
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) {
            throw new AiOutputParseException("AI 输出中未找到 ```" + language + " 代码块");
        }
        String block = matcher.group(1);
        if (block == null || block.isBlank()) {
            throw new AiOutputParseException("AI 输出中的 ```" + language + " 代码块为空");
        }
        return block;
    }

    private String extractHtmlDocument(String raw) {
        Matcher htmlFenceMatcher = HTML_BLOCK_PATTERN.matcher(raw);
        if (htmlFenceMatcher.find()) {
            return htmlFenceMatcher.group(1);
        }
        int htmlStart = indexOfHtmlDocument(raw);
        if (htmlStart >= 0) {
            return raw.substring(htmlStart).trim();
        }
        throw new AiOutputParseException("AI 输出中未找到完整 HTML");
    }

    private int indexOfHtmlDocument(String raw) {
        String lower = raw.toLowerCase();
        int doctype = lower.indexOf("<!doctype html");
        int htmlTag = lower.indexOf("<html");
        if (doctype >= 0) {
            return doctype;
        }
        return htmlTag;
    }

    private boolean isStandaloneHtmlDocument(String raw) {
        String trimmed = stripLeadingNoise(raw).trim();
        if (!looksLikeHtml(trimmed)) {
            return false;
        }
        int htmlStart = indexOfHtmlDocument(trimmed);
        if (htmlStart < 0) {
            return false;
        }
        String prefix = trimmed.substring(0, htmlStart).trim();
        return prefix.length() <= 40;
    }

    private boolean hasHtmlCodeBlocks(String raw) {
        return HTML_BLOCK_PATTERN.matcher(raw).find();
    }

    private String stripLeadingNoise(String raw) {
        String stripped = stripMarkdownHeaders(raw);
        return NATURAL_LANGUAGE_PREFIX.matcher(stripped.trim()).replaceFirst("").trim();
    }

    private String extractJson(String raw) {
        List<String> candidates = collectJsonCandidates(raw);
        if (candidates.isEmpty()) {
            throw new AiOutputParseException("AI 输出中未找到 JSON 结构");
        }
        String first = candidates.getFirst();
        if (!first.trim().endsWith("}")) {
            throw new AiOutputParseException("AI 输出中的 JSON 疑似被截断");
        }
        return first;
    }

    private int findMatchingBraceEnd(String value, int startIndex) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = startIndex; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String repairJson(String json) {
        String repaired = normalizeWhitespace(json).trim();
        repaired = sanitizeJsonStringLiterals(repaired);
        if (repaired.contains("':")) {
            repaired = SINGLE_QUOTED_KEY_PATTERN.matcher(repaired).replaceAll("\"$1\":");
            log.debug("Parser: recovered single-quoted keys");
        }
        if (repaired.contains(":'") || repaired.contains(": '")) {
            repaired = SINGLE_QUOTED_VALUE_PATTERN.matcher(repaired).replaceAll(": \"$1\"");
            log.debug("Parser: recovered single-quoted values");
        }
        String previous;
        do {
            previous = repaired;
            repaired = TRAILING_COMMA_PATTERN.matcher(repaired).replaceAll("$1");
        } while (!previous.equals(repaired));
        if (!previous.equals(repaired) || json.contains(",]") || json.contains(",}")) {
            log.debug("Parser: recovered trailing comma");
        }
        return repaired;
    }

    private String sanitizeJsonStringLiterals(String json) {
        StringBuilder out = new StringBuilder(json.length() + 64);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (!inString) {
                out.append(current);
                if (current == '"') {
                    inString = true;
                }
                continue;
            }
            if (escaped) {
                out.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                out.append(current);
                escaped = true;
                continue;
            }
            if (current == '"') {
                if (isJsonStringClosingQuote(json, i)) {
                    inString = false;
                    out.append(current);
                } else {
                    out.append('\\').append('"');
                    log.debug("Parser: escaped inner quote in JSON string");
                }
                continue;
            }
            if (current == '\n' || current == '\r') {
                out.append("\\n");
                log.debug("Parser: escaped newline in JSON string");
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }

    private boolean isJsonStringClosingQuote(String json, int quoteIndex) {
        int index = quoteIndex + 1;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        if (index >= json.length()) {
            return true;
        }
        char next = json.charAt(index);
        return next == ':' || next == ',' || next == '}' || next == ']';
    }

    private String normalizeWhitespace(String value) {
        return value.replace('\uFEFF', ' ').replace('\u00A0', ' ');
    }

    private GeneratedProject parseFileBlocks(String raw, String codeGenType) {
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(raw);
        List<GeneratedProjectFile> files = new ArrayList<>();
        while (matcher.find()) {
            String filePath = matcher.group(1) != null ? matcher.group(1).trim() : null;
            String fileContent = matcher.group(2);
            if (filePath == null || filePath.isBlank() || fileContent == null || fileContent.isBlank()) {
                continue;
            }
            String fileName = filePath.contains("/")
                    ? filePath.substring(filePath.lastIndexOf('/') + 1)
                    : filePath;
            if ("index.html".equalsIgnoreCase(fileName)) {
                ensureCompleteHtml(fileContent);
            }
            files.add(new GeneratedProjectFile(filePath, fileName, fileContent));
        }
        if (files.isEmpty()) {
            return null;
        }
        return new GeneratedProject("AI 生成文件", "AI Generated", codeGenType, "File block output", files);
    }

    private String readFilePath(JsonNode fileNode) {
        if (fileNode.hasNonNull("filePath")) {
            return fileNode.get("filePath").asText();
        }
        if (fileNode.hasNonNull("path")) {
            return fileNode.get("path").asText();
        }
        return null;
    }

    private String readFileName(JsonNode fileNode, String filePath) {
        if (fileNode.hasNonNull("fileName")) {
            return fileNode.get("fileName").asText();
        }
        if (filePath != null && filePath.contains("/")) {
            return filePath.substring(filePath.lastIndexOf('/') + 1);
        }
        return filePath != null ? filePath : "unknown";
    }

    private String readFileContent(JsonNode fileNode) {
        String raw;
        if (fileNode.hasNonNull("fileContent")) {
            raw = fileNode.get("fileContent").asText();
        } else if (fileNode.hasNonNull("content")) {
            raw = fileNode.get("content").asText();
        } else {
            return "";
        }
        String filePath = fileNode.hasNonNull("path") ? fileNode.get("path").asText()
                : fileNode.hasNonNull("filePath") ? fileNode.get("filePath").asText() : "index.html";
        return JsonEncodedFileContentDecoder.normalizeAfterParse(filePath, raw);
    }

    private boolean looksLikeHtml(String value) {
        String lowercase = value.toLowerCase();
        return lowercase.contains("<!doctype html") || lowercase.contains("<html");
    }

    private void ensureCompleteHtml(String html) {
        if (html == null || html.isBlank()) {
            throw new AiOutputParseException("AI 输出中的 HTML 为空");
        }
        String lowercase = html.toLowerCase();
        if (!lowercase.contains("<html") || !lowercase.contains("</html>")) {
            throw new AiOutputParseException("AI 输出中的 HTML 疑似被截断");
        }
    }

    public static class AiOutputParseException extends RuntimeException {
        public AiOutputParseException(String message) {
            super(message);
        }
    }
}
