package com.codeforge.ai.domain.prompt.model;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateRendererTest {

    @Test
    void shouldRenderVariablesInTemplate() {
        String rendered = PromptTemplateRenderer.render(
                "生成 {{appName}} 的页面",
                Map.of("appName", "CRM")
        );

        assertThat(rendered).isEqualTo("生成 CRM 的页面");
    }

    @Test
    void shouldRejectMissingVariable() {
        assertThatThrownBy(() -> PromptTemplateRenderer.render(
                "生成 {{appName}} 的页面",
                Map.of()
        )).hasMessageContaining("缺少变量");
    }

    @Test
    void shouldValidateRequiredVariablesBeforeRender() {
        assertThatThrownBy(() -> PromptTemplateRenderer.validateRequiredVariables(
                "系统提示",
                "用户需要 {{scene}}",
                Map.of("scene", " ")
        )).hasMessageContaining("缺少变量: scene");
    }
}
