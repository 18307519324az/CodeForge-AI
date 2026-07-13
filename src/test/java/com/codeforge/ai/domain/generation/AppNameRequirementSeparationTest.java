package com.codeforge.ai.domain.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppNameRequirementSeparationTest {

    private final AppDisplayNameDeriver deriver = new AppDisplayNameDeriver();

    @Test
    void appNameMustNotEqualFullRequirement() {
        String requirement = "生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。";
        String appName = deriver.deriveAppDisplayName(requirement, "ADMIN_WEB");
        assertThat(appName).isNotEqualTo(requirement);
        assertThat(appName).isEqualTo("客户管理后台");
        assertThat(requirement).contains("客户列表");
    }
}
