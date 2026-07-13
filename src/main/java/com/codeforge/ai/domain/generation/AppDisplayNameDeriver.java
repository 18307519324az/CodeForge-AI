package com.codeforge.ai.domain.generation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AppDisplayNameDeriver {

    private static final Pattern LEADING_PREFIX = Pattern.compile(
            "^(?:生成|创建|开发|设计)(?:一个|一套|一页)?|帮我(?:生成|创建|做(?:一个|一套|一页)?)");
    private static final Pattern BOUNDARY = Pattern.compile("[，,。]|(?:包含|需要|支持|具有)");
    private static final Pattern GREETING_ONLY = Pattern.compile("^(?:你好|您好|hi|hello|hey)[\\s!?.，。]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int MAX_NAME_LENGTH = 32;

    public boolean isGenerationRequirementValid(String requirement) {
        if (requirement == null) {
            return false;
        }
        String text = requirement.trim();
        if (text.isEmpty()) {
            return false;
        }
        if (GREETING_ONLY.matcher(text).matches()) {
            return false;
        }
        return text.length() >= 4;
    }

    public String deriveAppDisplayName(String requirement, String appType) {
        if (requirement == null || requirement.isBlank()) {
            return fallbackName(appType);
        }

        String text = requirement.trim();
        text = LEADING_PREFIX.matcher(text).replaceFirst("").trim();
        if (text.isEmpty()) {
            return fallbackName(appType);
        }

        Matcher boundaryMatcher = BOUNDARY.matcher(text);
        if (boundaryMatcher.find()) {
            text = text.substring(0, boundaryMatcher.start()).trim();
        }

        text = text.replaceAll("[，,。；;：:!！?？]+$", "").trim();
        if (text.isEmpty()) {
            return fallbackName(appType);
        }

        int length = text.codePointCount(0, text.length());
        if (length > MAX_NAME_LENGTH) {
            int endIndex = text.offsetByCodePoints(0, MAX_NAME_LENGTH);
            text = text.substring(0, endIndex);
        }
        return text.isBlank() ? fallbackName(appType) : text;
    }

    private String fallbackName(String appType) {
        if (appType == null || appType.isBlank()) {
            return "新建 Web 应用";
        }
        return switch (appType) {
            case "ADMIN_WEB" -> "新建管理后台";
            case "BLOG" -> "新建博客";
            case "OFFICIAL_SITE" -> "新建官网";
            default -> "新建 Web 应用";
        };
    }
}
