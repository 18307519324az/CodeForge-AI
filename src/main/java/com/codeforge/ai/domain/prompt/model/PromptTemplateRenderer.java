package com.codeforge.ai.domain.prompt.model;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptTemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private PromptTemplateRenderer() {
    }

    public static String render(String template, Map<String, String> variables) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = variables != null ? variables.get(key) : null;
            if (replacement == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少变量: " + key);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static void validateRequiredVariables(String systemPrompt, String userPrompt, Map<String, String> variables) {
        Set<String> requiredKeys = new LinkedHashSet<>();
        collectPlaceholders(systemPrompt, requiredKeys);
        collectPlaceholders(userPrompt, requiredKeys);
        if (requiredKeys.isEmpty()) {
            return;
        }
        if (variables == null || variables.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少模板变量: " + String.join(", ", requiredKeys));
        }
        for (String key : requiredKeys) {
            String value = variables.get(key);
            if (value == null || value.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少变量: " + key);
            }
        }
    }

    public static String truncatePreview(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private static void collectPlaceholders(String template, Set<String> keys) {
        if (template == null || template.isBlank()) {
            return;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
    }
}
