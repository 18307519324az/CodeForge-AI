package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * End-to-end AI_DIRECT path: invoker mock → CodeGenerationAiService → real parser.
 */
class AiDirectGenerationIntegrationTest {

    private CodeGenerationAiService service;

    @BeforeEach
    void setUp() {
        ModelGatewayInvoker invoker = mock(ModelGatewayInvoker.class);
        PromptResourceLoader loader = new PromptResourceLoader();
        service = new CodeGenerationAiService(invoker, new AiGeneratedProjectParser(), new GeneratedArtifactValidator(), loader);
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any())).willAnswer(invocation -> {
            GenerationContext ctx = invocation.getArgument(1);
            return successResult(buildModelOutput(ctx.requirement()));
        });
    }

    @ParameterizedTest
    @CsvSource({
            "生成一个待办清单页面,今日任务,待办",
            "生成一个电商后台管理页面,商城后台,商城",
            "生成一个 CRM 客户管理页面,客户管理,CRM",
            "生成一个工单系统页面,工单中心,工单"
    })
    void shouldProduceAiDirectProjectForCorePrompts(String requirement, String appName, String keyword) {
        GeneratedProject project = service.generate(context(requirement, appName));

        assertThat(project.files()).isNotEmpty();
        assertThat(project.files().getFirst().content()).containsIgnoringCase("html");
        assertThat(project.summary()).isNotBlank();
        assertThat(project.files().getFirst().content()).contains(keyword);
    }

    @Test
    void shouldRecoverAiDirectWhenModelWrapsJsonInMarkdown() {
        ModelGatewayInvoker invoker = mock(ModelGatewayInvoker.class);
        PromptResourceLoader loader = new PromptResourceLoader();
        CodeGenerationAiService wrappedService =
                new CodeGenerationAiService(invoker, new AiGeneratedProjectParser(), new GeneratedArtifactValidator(), loader);

        String markdownWrapped = """
                好的，以下是结果：
                ```json
                {"projectName":"待办","description":"d","files":[{"path":"index.html","content":"<!doctype html><html lang=\\"zh-CN\\"><head><meta charset=\\"UTF-8\\"><title>待办</title></head><body><main>待办</main></body></html>"}]}
                ```
                """;
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(successResult(markdownWrapped));

        GeneratedProject project = wrappedService.generate(context("生成待办", "待办"));

        assertThat(project.summary()).isEqualTo("待办");
        assertThat(project.files()).hasSize(1);
    }

    private ModelChatResult successResult(String content) {
        return ModelChatResult.success(content, "stop", 10L, 20L, 30L, 100L, "deepseek", "deepseek-chat");
    }

    private String buildModelOutput(String requirement) {
        String title = switch (requirement) {
            case String r when r.contains("待办") -> "待办";
            case String r when r.contains("电商") -> "商城";
            case String r when r.contains("CRM") -> "CRM";
            case String r when r.contains("工单") -> "工单";
            default -> "应用";
        };
        String html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>"
                + title + "</title></head><body><main>" + title + "</main></body></html>";
        return """
                {"projectName":"%s","description":"generated","files":[{"path":"index.html","content":"%s"}]}
                """.formatted(title, html.replace("\"", "\\\""));
    }

    private GenerationContext context(String requirement, String appName) {
        return new GenerationContext(
                requirement,
                appName,
                "WEB_APP",
                "HTML",
                1L, 2L, 3L,
                null, null, null, null, null, null
        );
    }
}
