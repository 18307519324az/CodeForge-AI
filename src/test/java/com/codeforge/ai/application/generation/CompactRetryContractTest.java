package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.prompt.GenerationOutputSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompactRetryContractTest {

    @Test
    void compactRetryUsesStrictCompactContractTest() {
        assertThat(AiCodegenPromptBuilder.STRICT_COMPACT_USER_INSTRUCTION)
                .contains("STRICT compact contract")
                .contains("Regenerate from scratch")
                .contains(GenerationOutputSchema.FILES_ONLY_SCHEMA);
    }

    @Test
    void compactRetryDoesNotReuseRichMustIncludeTest() {
        String compactInstruction = AiCodegenPromptBuilder.STRICT_COMPACT_USER_INSTRUCTION;
        String richUserPrompt = AiCodegenPromptBuilder.buildUserPrompt(richContext());

        assertThat(compactInstruction).doesNotContain("At least two statistics or metric cards");
        assertThat(compactInstruction).doesNotContain("At least three distinct page modules");
        assertThat(richUserPrompt).contains("At least two statistics or metric cards");
    }

    @Test
    void compactRetryDoesNotRequireDescriptionTest() {
        assertThat(AiCodegenPromptBuilder.STRICT_COMPACT_USER_INSTRUCTION)
                .doesNotContain("\"description\"")
                .doesNotContain("\"projectName\"");
    }

    @Test
    void compactRetryLimitsMockRowsTest() {
        assertThat(AiCodegenPromptBuilder.STRICT_COMPACT_USER_INSTRUCTION)
                .contains("at most 3 mock rows");
    }

    @Test
    void compactRetryDoesNotForceChartsTest() {
        assertThat(AiCodegenPromptBuilder.STRICT_COMPACT_USER_INSTRUCTION)
                .contains("Do not add charts unless the user explicitly requested charts");
    }

    @Test
    void compactRetryKeepsOriginalCoreRequirementTest() {
        GenerationContext context = minimalContext();
        var messages = AiCodegenPromptBuilder.buildCompactMessages("system", context);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(1).content()).contains("生成一个极简待办清单页面");
        assertThat(messages.get(1).content()).contains("今日任务");
        assertThat(messages.get(1).content()).doesNotContain("At least two statistics or metric cards");
    }

    @Test
    void compactRetryDoesNotIncludePartialOutputTest() {
        var messages = AiCodegenPromptBuilder.buildCompactMessages("system", minimalContext());

        assertThat(messages.get(1).content()).doesNotContain("partial");
        assertThat(messages.get(2).content()).doesNotContain("partial");
        assertThat(messages.stream().map(m -> m.content()).noneMatch(content -> content.contains("previous response content")));
    }

    private GenerationContext minimalContext() {
        return new GenerationContext(
                "生成一个极简待办清单页面，只要 index.html",
                "今日任务",
                "WEB_APP",
                "HTML",
                1L, 2L, 3L,
                null, null, null, null, null, null);
    }

    private GenerationContext richContext() {
        return new GenerationContext(
                "生成企业级综合后台 dashboard，包含数据分析图表和多模块管理",
                "运营后台",
                "DASHBOARD",
                "HTML",
                1L, 2L, 3L,
                null, null, null, null, null, null);
    }
}
