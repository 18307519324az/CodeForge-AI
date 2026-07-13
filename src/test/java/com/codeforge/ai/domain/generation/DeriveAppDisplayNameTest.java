package com.codeforge.ai.domain.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeriveAppDisplayNameTest {

    private final AppDisplayNameDeriver deriver = new AppDisplayNameDeriver();

    @Test
    void shouldDeriveCrmTitle() {
        assertThat(deriver.deriveAppDisplayName(
                "生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。", "WEB_APP"))
                .isEqualTo("客户管理后台");
    }

    @Test
    void shouldDeriveBlogTitle() {
        assertThat(deriver.deriveAppDisplayName(
                "生成一个个人博客系统，包含文章列表、文章详情和分类标签功能。", "WEB_APP"))
                .isEqualTo("个人博客系统");
    }

    @Test
    void shouldDeriveTicketTitle() {
        assertThat(deriver.deriveAppDisplayName(
                "创建一个工单管理系统，支持工单提交、流转和状态看板。", "WEB_APP"))
                .isEqualTo("工单管理系统");
    }

    @Test
    void shouldDeriveMallTitle() {
        assertThat(deriver.deriveAppDisplayName("帮我做一个商城", "WEB_APP"))
                .isEqualTo("商城");
    }

    @Test
    void shouldRejectGreetingOnlyRequirement() {
        assertThat(deriver.isGenerationRequirementValid("你好")).isFalse();
    }

    @Test
    void shouldRejectBlankRequirement() {
        assertThat(deriver.isGenerationRequirementValid("   ")).isFalse();
    }

    @Test
    void shouldFallbackByAppType() {
        assertThat(deriver.deriveAppDisplayName("", "ADMIN_WEB"))
                .isEqualTo("新建管理后台");
    }
}
