package com.codeforge.ai.domain.generation.scope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationScopePolicyTest {

    private final GenerationScopePolicy policy = new GenerationScopePolicy();

    @Test
    void minimalTodoUsesMinimalScopeTest() {
        String requirement = "生成一个极简待办清单页面，只要 index.html，包含输入框、添加按钮、待办列表和完成状态切换";

        GenerationScope scope = policy.resolve(requirement, "WEB_APP");

        assertThat(scope).isEqualTo(GenerationScope.MINIMAL);
        assertThat(policy.requiresCharts(scope, requirement)).isFalse();
        assertThat(policy.requiresStatisticsCards(scope)).isFalse();
        assertThat(policy.requiresMultipleCrudModules(scope)).isFalse();
        assertThat(policy.maxMockRows(scope)).isEqualTo(3);
    }

    @Test
    void simpleBlogDoesNotForceChartsTest() {
        String requirement = "生成一个简洁的个人博客首页，包含文章列表和分类筛选";

        GenerationScope scope = policy.resolve(requirement, "WEB_APP");

        assertThat(scope).isEqualTo(GenerationScope.STANDARD);
        assertThat(policy.requiresCharts(scope, requirement)).isFalse();
        assertThat(policy.requiresStatisticsCards(scope)).isFalse();
    }

    @Test
    void crmUsesStandardScopeTest() {
        String requirement = "生成客户管理后台，包含客户列表、客户详情和客户编辑表单";

        GenerationScope scope = policy.resolve(requirement, "CRM");

        assertThat(scope).isEqualTo(GenerationScope.STANDARD);
        assertThat(policy.requiresMultipleCrudModules(scope)).isFalse();
    }

    @Test
    void explicitDashboardUsesRichScopeTest() {
        String requirement = "生成企业级综合后台 dashboard，包含数据分析图表和多模块管理";

        GenerationScope scope = policy.resolve(requirement, "DASHBOARD");

        assertThat(scope).isEqualTo(GenerationScope.RICH);
        assertThat(policy.requiresCharts(scope, requirement)).isTrue();
        assertThat(policy.requiresStatisticsCards(scope)).isTrue();
        assertThat(policy.requiresMultipleCrudModules(scope)).isTrue();
    }

    @Test
    void minimalScopeDoesNotRequireMultipleCrudModulesTest() {
        GenerationScope scope = policy.resolve("生成一个简单待办页面", "WEB_APP");

        assertThat(scope).isEqualTo(GenerationScope.MINIMAL);
        assertThat(policy.requiresMultipleCrudModules(scope)).isFalse();
    }

    @Test
    void minimalScopeLimitsMockRowsTest() {
        GenerationScope minimal = policy.resolve("生成极简计数器", "WEB_APP");
        GenerationScope standard = policy.resolve("生成客户管理后台", "CRM");

        assertThat(policy.maxMockRows(minimal)).isEqualTo(3);
        assertThat(policy.maxMockRows(standard)).isEqualTo(5);
    }
}
