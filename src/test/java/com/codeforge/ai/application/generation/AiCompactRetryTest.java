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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiCompactRetryTest {

    @Mock
    private ModelGatewayInvoker invoker;

    @Spy
    private AiGeneratedProjectParser parser = new AiGeneratedProjectParser();

    @Spy
    private GeneratedArtifactValidator artifactValidator = new GeneratedArtifactValidator();

    @Mock
    private PromptResourceLoader promptLoader;

    @InjectMocks
    private CodeGenerationAiService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "configuredMaxTokens", 8192);
        given(promptLoader.load("codegen-html-system-prompt.txt")).willReturn("system prompt");
    }

    @Test
    void shouldUseCompactRetryAndSucceedOnSecondAttempt() {
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(ModelChatResult.success("partial", "length", 100L, 8192L, 8292L, 100L, "deepseek", "deepseek-chat"))
                .willReturn(successResult(validJson()));

        GeneratedProject project = service.generate(context());

        assertThat(project.files()).hasSize(1);
        verify(invoker, times(2)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
    }

    private ModelChatResult successResult(String content) {
        return ModelChatResult.success(content, "stop", 100L, 500L, 600L, 100L, "deepseek", "deepseek-chat");
    }

    private String validJson() {
        return """
                {"projectName":"CRM","description":"d","files":[{"path":"index.html","content":"<!doctype html><html lang=\\"zh-CN\\"><head><meta charset=\\"UTF-8\\"><title>CRM</title></head><body></body></html>"}]}
                """;
    }

    private GenerationContext context() {
        return new GenerationContext(
                "生成 CRM",
                "CRM",
                "WEB_APP",
                "HTML",
                1L, 2L, 3L,
                null, null, null, null, null, null
        );
    }
}
