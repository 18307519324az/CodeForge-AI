package com.codeforge.ai.domain.generation.business;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessPresetTest {

    @Test
    void shouldResolveTodoPresetFromRequirement() {
        BusinessPreset preset = BusinessPresetRegistry.resolve("WEB_APP", "生成一个待办清单页面，包含提醒和完成率");

        assertThat(preset.businessType()).isEqualTo("TODO");
        assertThat(preset.modules()).contains("今日任务", "标签管理", "完成率统计");
        assertThat(preset.interactions()).contains("搜索", "过滤", "拖拽");
    }

    @Test
    void shouldResolveEcommercePresetFromRequirement() {
        BusinessPreset preset = BusinessPresetRegistry.resolve("WEB_APP", "生成一个电商商城后台，包含商品、订单和库存");

        assertThat(preset.businessType()).isEqualTo("ECOMMERCE");
        assertThat(preset.modules()).contains("商品管理", "订单管理", "库存管理", "分类管理");
    }

    @Test
    void shouldResolveCrmPresetFromAppType() {
        BusinessPreset preset = BusinessPresetRegistry.resolve("CRM", "生成一个客户运营工作台");

        assertThat(preset.businessType()).isEqualTo("CRM");
        assertThat(preset.modules()).contains("客户列表", "联系人", "跟进记录", "销售漏斗");
    }

    @Test
    void shouldResolveDashboardAndPortalPresetFromAppType() {
        assertThat(BusinessPresetRegistry.resolve("DASHBOARD", "生成数据分析页面").businessType())
                .isEqualTo("DASHBOARD");
        assertThat(BusinessPresetRegistry.resolve("PORTAL", "生成企业入口").businessType())
                .isEqualTo("PORTAL");
    }
}
