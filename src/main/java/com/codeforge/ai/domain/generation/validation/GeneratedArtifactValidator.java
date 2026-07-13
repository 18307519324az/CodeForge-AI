package com.codeforge.ai.domain.generation.validation;

import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.parser.JsonEncodedFileContentDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GeneratedArtifactValidator {

    private static final Pattern HTML_ENTRY = Pattern.compile("index\\.html$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCAPE_CORRUPTION = Pattern.compile(
            "(?is)(?:<!doctype\\s+html>\\s*)n\\s*<html|</\\w+>\\s*n\\s*<|>\\s*n\\s*<(html|head|body|style|script|div|main|section|nav|footer|header)\\b>");
    private static final Pattern LOCAL_ASSET = Pattern.compile(
            "(?is)(?:href|src)\\s*=\\s*[\"']([^\"'#?]+)[\"']");
    private static final Pattern MARKDOWN_FENCE = Pattern.compile("```");

    public ArtifactValidationResult validate(GeneratedProject project, String codeGenType) {
        List<String> issues = new ArrayList<>();
        String errorCode = null;

        if (project == null || project.files() == null || project.files().isEmpty()) {
            return ArtifactValidationResult.invalid(
                    AiArtifactErrorCodes.ENTRY_MISSING,
                    List.of("生成结果没有任何文件"));
        }

        Set<String> normalizedPaths = new HashSet<>();
        Map<String, GeneratedProjectFile> filesByPath = project.files().stream()
                .filter(file -> file.filePath() != null && !file.filePath().isBlank())
                .collect(Collectors.toMap(
                        file -> normalizePath(file.filePath()),
                        file -> file,
                        (left, right) -> left));

        for (GeneratedProjectFile file : project.files()) {
            String path = file.filePath();
            if (path == null || path.isBlank()) {
                issues.add("存在空文件路径");
                continue;
            }
            String normalized = normalizePath(path);
            if (!normalizedPaths.add(normalized)) {
                issues.add("文件路径重复: " + path);
            }
            if (normalized.contains("..") || normalized.startsWith("/") || normalized.contains("\\")) {
                issues.add("非法文件路径: " + path);
            }
            if (file.content() == null || file.content().isBlank()) {
                issues.add("文件内容为空: " + path);
            }
        }

        GeneratedProjectFile entry = findEntry(project.files());
        if (entry == null) {
            return ArtifactValidationResult.invalid(
                    AiArtifactErrorCodes.ENTRY_MISSING,
                    List.of("缺少 index.html 入口文件"));
        }

        String entryContent = entry.content();
        if (entryContent == null || entryContent.isBlank()) {
            return ArtifactValidationResult.invalid(
                    AiArtifactErrorCodes.ENTRY_MISSING,
                    List.of("入口 HTML 内容为空"));
        }

        if (!isHtmlEntry(codeGenType)) {
            return issues.isEmpty()
                    ? ArtifactValidationResult.valid()
                    : ArtifactValidationResult.invalid(AiArtifactErrorCodes.INVALID, issues);
        }

        validateHtmlEntry(entryContent, issues);
        validateLinkedAssets(entryContent, filesByPath, issues);

        if (!issues.isEmpty()) {
            errorCode = issues.stream().anyMatch(issue -> issue.contains("转义"))
                    ? AiArtifactErrorCodes.ESCAPE_CORRUPTED
                    : issues.stream().anyMatch(issue -> issue.contains("资源"))
                            ? AiArtifactErrorCodes.ASSET_MISSING
                            : AiArtifactErrorCodes.INVALID;
            return ArtifactValidationResult.invalid(errorCode, issues);
        }
        return ArtifactValidationResult.valid();
    }

    private void validateHtmlEntry(String html, List<String> issues) {
        String lower = html.toLowerCase(Locale.ROOT);
        if (!lower.contains("<html") || !lower.contains("</html>")) {
            issues.add("入口 HTML 缺少 html 结构");
        }
        if (!lower.contains("<head") || !lower.contains("<body")) {
            issues.add("入口 HTML 缺少 head/body 结构");
        }
        if (MARKDOWN_FENCE.matcher(html).find()) {
            issues.add("入口 HTML 包含未闭合 Markdown fence");
        }
        if (detectEscapeCorruption(html)) {
            issues.add("入口 HTML 存在换行转义损坏");
        }
        if (detectLiteralBackslashN(html)) {
            issues.add("入口 HTML 存在字面量换行转义");
        }
    }

    boolean detectLiteralBackslashN(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return JsonEncodedFileContentDecoder.shouldDecodeNestedJsonString("index.html", content);
    }

    private void validateLinkedAssets(String html,
                                      Map<String, GeneratedProjectFile> filesByPath,
                                      List<String> issues) {
        Matcher matcher = LOCAL_ASSET.matcher(html);
        while (matcher.find()) {
            String reference = matcher.group(1).trim();
            if (reference.isBlank() || isRemoteOrInline(reference)) {
                continue;
            }
            String normalized = normalizePath(reference);
            if (!filesByPath.containsKey(normalized)) {
                issues.add("入口 HTML 引用了缺失的本地资源: " + reference);
            }
        }
    }

    private GeneratedProjectFile findEntry(List<GeneratedProjectFile> files) {
        for (GeneratedProjectFile file : files) {
            if (file.fileName() != null && HTML_ENTRY.matcher(file.fileName()).find()) {
                return file;
            }
            if (file.filePath() != null && HTML_ENTRY.matcher(file.filePath()).find()) {
                return file;
            }
        }
        return null;
    }

    private boolean isHtmlEntry(String codeGenType) {
        if (codeGenType == null || codeGenType.isBlank()) {
            return true;
        }
        return "HTML".equalsIgnoreCase(codeGenType)
                || "MULTI_FILE".equalsIgnoreCase(codeGenType)
                || "WEB_APP".equalsIgnoreCase(codeGenType);
    }

    boolean detectEscapeCorruption(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        int lfCount = 0;
        for (byte value : bytes) {
            if (value == 0x0A) {
                lfCount++;
            }
        }
        if (lfCount > 0) {
            return false;
        }
        if (content.length() < 80) {
            return false;
        }
        return ESCAPE_CORRUPTION.matcher(content).find();
    }

    private boolean isRemoteOrInline(String reference) {
        String lower = reference.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//")) {
            return true;
        }
        if (lower.startsWith("data:") || lower.startsWith("mailto:") || lower.startsWith("#")) {
            return true;
        }
        try {
            URI uri = URI.create(reference);
            return uri.getScheme() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/').replaceAll("^\\./+", "").trim().toLowerCase(Locale.ROOT);
    }
}
