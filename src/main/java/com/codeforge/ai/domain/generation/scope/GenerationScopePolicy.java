package com.codeforge.ai.domain.generation.scope;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic generation scope resolver. Never uses AI classification.
 */
public final class GenerationScopePolicy {

    private static final List<String> RICH_KEYWORDS = List.of(
            "dashboard", "数据看板", "数据分析", "数据面板", "统计面板",
            "图表", "chart", "企业级", "多模块", "综合后台", "指标总览", "趋势分析");

    private static final List<String> MINIMAL_KEYWORDS = List.of(
            "极简", "简单", "单页", "single page", "todo", "待办", "计数器", "counter",
            "简单表单", "landing", "只要 index.html", "只要index.html");

    public GenerationScope resolve(String requirement, String appType) {
        String normalizedRequirement = normalize(requirement);
        String normalizedAppType = normalize(appType);

        if (containsAny(normalizedRequirement, RICH_KEYWORDS)
                || containsAny(normalizedAppType, List.of("dashboard"))) {
            return GenerationScope.RICH;
        }
        if (containsAny(normalizedRequirement, MINIMAL_KEYWORDS)) {
            return GenerationScope.MINIMAL;
        }
        return GenerationScope.STANDARD;
    }

    public int maxMockRows(GenerationScope scope) {
        return switch (scope) {
            case MINIMAL -> 3;
            case STANDARD -> 5;
            case RICH -> 5;
        };
    }

    public int targetSourceChars(GenerationScope scope) {
        return switch (scope) {
            case MINIMAL -> 12_000;
            case STANDARD -> 18_000;
            case RICH -> 18_000;
        };
    }

    public boolean requiresCharts(GenerationScope scope, String requirement) {
        if (scope == GenerationScope.RICH) {
            return true;
        }
        String normalized = normalize(requirement);
        return normalized.contains("图表") || normalized.contains("chart") || normalized.contains("dashboard");
    }

    public boolean requiresStatisticsCards(GenerationScope scope) {
        return scope == GenerationScope.RICH;
    }

    public boolean requiresMultipleCrudModules(GenerationScope scope) {
        return scope == GenerationScope.RICH;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim();
    }
}
