package com.codeforge.ai.application.service;

import java.util.Locale;
import java.util.regex.Pattern;

final class BrandAssetReferenceRewriter {

    static final String GENERATED_MASCOT_RELATIVE_PATH = "assets/codeforge-mascot.png";

    private static final Pattern LEGACY_SRC = Pattern.compile(
            "(?i)(src|href)\\s*=\\s*[\"']([^\"']*(?:aiavatar|logo|yupi|鱼皮)[^\"']*)[\"']");
    private static final Pattern LEGACY_URL = Pattern.compile(
            "(?i)url\\((['\"]?)([^\"')]*(?:aiavatar|logo|yupi|鱼皮)[^\"')]*)\\1\\)");

    private BrandAssetReferenceRewriter() {
    }

    static String rewriteGeneratedContent(String filePath, String content) {
        if (content == null || content.isBlank() || filePath == null) {
            return content;
        }
        String lower = filePath.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".html") && !lower.endsWith(".css")) {
            return content;
        }
        String rewritten = LEGACY_SRC.matcher(content).replaceAll(
                "$1=\"" + relativeMascotRef(filePath) + "\"");
        rewritten = LEGACY_URL.matcher(rewritten).replaceAll(
                "url('" + relativeMascotRef(filePath) + "')");
        return rewritten;
    }

    private static String relativeMascotRef(String filePath) {
        if (filePath.contains("/")) {
            int depth = filePath.replace('\\', '/').split("/").length - 1;
            if (depth <= 0) {
                return GENERATED_MASCOT_RELATIVE_PATH;
            }
            return "../".repeat(depth) + GENERATED_MASCOT_RELATIVE_PATH;
        }
        return GENERATED_MASCOT_RELATIVE_PATH;
    }
}
