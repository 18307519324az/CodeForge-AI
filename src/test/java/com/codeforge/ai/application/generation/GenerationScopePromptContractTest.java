package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.scope.GenerationScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationScopePromptContractTest {

    @Test
    void explicitUserRequirementSurvivesScopeBudgetTest() {
        String requirement = "生成一个简单的客户管理页面，包含销售图表和客户列表";
        GenerationContext context = context("CRM", requirement);

        assertThat(AiCodegenPromptBuilder.resolveScope(context)).isEqualTo(GenerationScope.RICH);

        String prompt = AiCodegenPromptBuilder.buildUserPrompt(context);
        assertThat(prompt).contains("RICH");
        assertThat(prompt).contains("销售图表");
        assertThat(prompt).contains("At least one lightweight chart-like visualization built with div bars or simple CSS.");
        assertThat(prompt).doesNotContain("Do not add charts unless the user explicitly requested charts.");
    }

    @Test
    void explicitCrudRequirementSurvivesMinimalBudgetTest() {
        String requirement = "生成极简待办页面，必须支持添加、编辑、删除三条 CRUD 操作";
        GenerationContext context = context("WEB_APP", requirement);

        assertThat(AiCodegenPromptBuilder.resolveScope(context)).isEqualTo(GenerationScope.MINIMAL);

        String prompt = AiCodegenPromptBuilder.buildUserPrompt(context);
        assertThat(prompt).contains("MINIMAL");
        assertThat(prompt).contains(requirement);
        assertThat(prompt).contains("添加、编辑、删除三条 CRUD 操作");
        assertThat(prompt).contains("Do not require charts, statistics cards, or multiple CRUD modules.");
        assertThat(prompt).doesNotContain("At least two CRUD interactions.");
    }

    @Test
    void minimalTodoDoesNotForceRichFeaturesTest() {
        String requirement = "生成一个待办清单页面，包含分类、标签、提醒和完成率";
        GenerationContext context = context("WEB_APP", requirement);

        assertThat(AiCodegenPromptBuilder.resolveScope(context)).isEqualTo(GenerationScope.MINIMAL);

        String prompt = AiCodegenPromptBuilder.buildUserPrompt(context);
        assertThat(prompt).contains("MINIMAL");
        assertThat(prompt).contains("Core requirement only");
        assertThat(prompt).doesNotContain("At least three distinct page modules or sections.");
        assertThat(prompt).doesNotContain("At least two statistics or metric cards.");
        assertThat(prompt).doesNotContain("At least one lightweight chart-like visualization built with div bars or simple CSS.");
        assertThat(prompt).doesNotContain("At least two CRUD interactions.");
    }

    private GenerationContext context(String appType, String requirement) {
        return new GenerationContext(
                requirement,
                "Scope Contract App",
                appType,
                "HTML",
                1L,
                2L,
                3L,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
