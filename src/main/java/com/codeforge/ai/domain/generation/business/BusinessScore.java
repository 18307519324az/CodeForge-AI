package com.codeforge.ai.domain.generation.business;

import java.util.List;

public record BusinessScore(
        int score,
        String businessType,
        int moduleCount,
        int entityCount,
        int crudCount,
        int statisticCount,
        int chartCount,
        boolean hasSearch,
        boolean hasFilter,
        List<String> matchedModules,
        List<String> matchedEntities
) {
    public boolean passed(int threshold) {
        return score >= threshold;
    }
}
