package com.codeforge.ai.domain.generation.business;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BusinessScoreEvaluator {

    public static final int PASSING_SCORE = 50;

    private static final List<String> CRUD_TERMS = List.of(
            "新增", "创建", "编辑", "修改", "删除", "详情", "提交", "分配", "更新", "完成",
            "add", "create", "edit", "update", "delete", "detail", "submit", "assign");
    private static final List<String> STATISTIC_TERMS = List.of(
            "统计", "总数", "趋势", "完成率", "转化率", "达成率", "销售额", "数量",
            "stat", "metric", "trend", "rate", "summary", "total");
    private static final List<String> CHART_TERMS = List.of(
            "图表", "折线", "柱状", "漏斗", "分布", "环比", "同比", "chart", "graph", "funnel", "bar", "line");
    private static final List<String> SEARCH_TERMS = List.of("搜索", "查询", "search");
    private static final List<String> FILTER_TERMS = List.of("筛选", "过滤", "filter");

    private BusinessScoreEvaluator() {
    }

    public static BusinessScore evaluate(String content, BusinessPreset preset) {
        String source = content == null ? "" : content;
        String lower = source.toLowerCase(Locale.ROOT);

        List<String> matchedModules = matchedTerms(source, lower, preset.modules());
        List<String> matchedEntities = matchedTerms(source, lower, preset.entities());
        int crudCount = countMatches(source, lower, CRUD_TERMS);
        int statisticCount = countMatches(source, lower, STATISTIC_TERMS);
        int chartCount = countMatches(source, lower, CHART_TERMS);
        boolean hasSearch = containsAny(source, lower, SEARCH_TERMS);
        boolean hasFilter = containsAny(source, lower, FILTER_TERMS);

        int score = 0;
        score += Math.min(matchedModules.size(), 4) * 10;
        score += Math.min(matchedEntities.size(), 4) * 8;
        score += Math.min(crudCount, 4) * 5;
        score += Math.min(statisticCount, 2) * 8;
        score += Math.min(chartCount, 1) * 8;
        score += hasSearch ? 5 : 0;
        score += hasFilter ? 5 : 0;
        score = Math.min(score, 100);

        return new BusinessScore(
                score,
                preset.businessType(),
                matchedModules.size(),
                matchedEntities.size(),
                crudCount,
                statisticCount,
                chartCount,
                hasSearch,
                hasFilter,
                matchedModules,
                matchedEntities);
    }

    private static List<String> matchedTerms(String source, String lower, List<String> terms) {
        List<String> matched = new ArrayList<>();
        for (String term : terms) {
            if (containsTerm(source, lower, term)) {
                matched.add(term);
            }
        }
        return matched;
    }

    private static int countMatches(String source, String lower, List<String> terms) {
        int count = 0;
        for (String term : terms) {
            if (containsTerm(source, lower, term)) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsAny(String source, String lower, List<String> terms) {
        for (String term : terms) {
            if (containsTerm(source, lower, term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTerm(String source, String lower, String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        boolean asciiOnly = term.chars().allMatch(ch -> ch < 128);
        return asciiOnly ? lower.contains(term.toLowerCase(Locale.ROOT)) : source.contains(term);
    }
}
