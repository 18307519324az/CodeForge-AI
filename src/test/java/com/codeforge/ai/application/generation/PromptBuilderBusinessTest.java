package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.scope.GenerationScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderBusinessTest {

    @Test
    void shouldBuildMinimalPromptForTodoRequirement() {
        String requirement = "生成一个待办清单页面，包含分类、标签、提醒和完成率";
        String prompt = AiCodegenPromptBuilder.buildUserPrompt(context("WEB_APP", requirement));

        assertCommonPromptStructure(prompt);
        assertThat(prompt).contains("Generation Scope");
        assertThat(prompt).contains("MINIMAL");
        assertThat(prompt).contains("Business Type: TODO");
        assertThat(prompt).contains("Core requirement only");
        assertThat(prompt).contains("Do not require charts, statistics cards, or multiple CRUD modules.");
        assertThat(prompt).contains("Target total source code <= 12000 characters.");
        assertThat(prompt).doesNotContain("At least three distinct page modules or sections.");
        assertThat(prompt).doesNotContain("Keep the index.html content under 7000 characters.");
    }

    @ParameterizedTest
    @CsvSource({
            "CRM,生成一个 CRM 客户管理页面，包含客户、联系人、跟进记录和销售漏斗,CRM,客户列表,联系人",
            "ADMIN,生成一个工单系统，包含工单列表、评论、附件和 SLA 统计,TICKET,工单列表,评论协作",
            "ECOMMERCE,生成一个电商商城后台，包含商品、订单、库存和分类,ECOMMERCE,商品管理,订单管理",
            "PORTAL,生成一个企业门户，包含入口导航、公告和快捷操作,PORTAL,入口导航,公告中心"
    })
    void shouldBuildStandardScopePromptForBusinessPreset(
            String appType,
            String requirement,
            String businessType,
            String expectedModule,
            String expectedSecondModule) {
        String prompt = AiCodegenPromptBuilder.buildUserPrompt(context(appType, requirement));

        assertCommonPromptStructure(prompt);
        assertThat(prompt).contains("Generation Scope");
        assertThat(prompt).contains("STANDARD");
        assertThat(prompt).contains("Business Type: " + businessType);
        assertThat(prompt).contains(expectedModule);
        assertThat(prompt).contains(expectedSecondModule);
        assertThat(prompt).contains("Generate only modules explicitly mentioned in the requirement.");
        assertThat(prompt).contains("Do not add charts unless the user explicitly requested charts.");
        assertThat(prompt).contains("Target total source code <= 18000 characters.");
        assertThat(prompt).doesNotContain("At least three distinct page modules or sections.");
        assertThat(prompt).doesNotContain("Keep the index.html content under 7000 characters.");
    }

    @Test
    void shouldBuildRichPromptForDashboardRequirement() {
        String requirement = "生成一个数据分析看板，包含趋势、异常和维度筛选";
        String prompt = AiCodegenPromptBuilder.buildUserPrompt(context("DASHBOARD", requirement));

        assertCommonPromptStructure(prompt);
        assertThat(prompt).contains("Generation Scope");
        assertThat(prompt).contains("RICH");
        assertThat(prompt).contains("Business Type: DASHBOARD");
        assertThat(prompt).contains("指标总览");
        assertThat(prompt).contains("趋势分析");
        assertThat(prompt).contains("At least three distinct page modules or sections.");
        assertThat(prompt).contains("At least one lightweight chart-like visualization built with div bars or simple CSS.");
        assertThat(prompt).contains("Target total source code <= 18000 characters.");
        assertThat(prompt).doesNotContain("Keep the index.html content under 7000 characters.");
    }

    private void assertCommonPromptStructure(String prompt) {
        assertThat(prompt).contains("Role");
        assertThat(prompt).contains("Goal");
        assertThat(prompt).contains("Business Context");
        assertThat(prompt).contains("Business Modules");
        assertThat(prompt).contains("Business Workflow");
        assertThat(prompt).contains("Business Interaction");
        assertThat(prompt).contains("Must Include");
        assertThat(prompt).contains("Must Avoid");
        assertThat(prompt).contains("Output Budget");
        assertThat(prompt).contains("Output JSON");
        assertThat(prompt).contains("Generate exactly one compact index.html file.");
        assertThat(prompt).contains("Do not generate separate files other than index.html.");
    }

    private GenerationContext context(String appType, String requirement) {
        return new GenerationContext(
                requirement,
                "Business App",
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
