package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser.AiOutputParseException;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CodeGenerationAiServiceRetryTest {

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
        given(promptLoader.load("codegen-html-system-prompt.txt")).willReturn("system prompt");
    }

    @Test
    void shouldSucceedOnFirstAttemptWithoutRetry() {
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any())).willReturn(successResult(validJson()));

        GeneratedProject project = service.generate(context());

        assertThat(project.files()).hasSize(1);
        verify(invoker, times(1)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void shouldRetryOnceWhenFirstParseFails() {
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(successResult("好的，我来生成。"))
                .willReturn(successResult(validJson()));

        GeneratedProject project = service.generate(context());

        assertThat(project.summary()).contains("待办");
        verify(invoker, times(2)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void shouldFallbackAfterTwoFailedAttempts() {
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(successResult("Sure! Here's your code"))
                .willReturn(successResult("抱歉，无法生成"));

        assertThatThrownBy(() -> service.generate(context()))
                .isInstanceOf(AiGenerationFailureException.class)
                .extracting(error -> ((AiGenerationFailureException) error).errorCode())
                .isEqualTo(AiGenerationFailureException.AI_OUTPUT_INVALID_JSON);

        verify(invoker, times(2)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void shouldRetryWhenFirstResponseIsMarkdownWrappedJson() {
        String markdownJson = """
                ### Result
                ```json
                {"projectName":"x","description":"d","files":[{"path":"index.html","content":"<!doctype html><html lang=\\"zh-CN\\"><head><meta charset=\\"UTF-8\\"><title>待办</title></head><body></body></html>"}]}
                ```
                """;
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(successResult(markdownJson));

        GeneratedProject project = service.generate(context());

        assertThat(project.files().getFirst().content()).contains("待办");
        verify(invoker, times(1)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void shouldUseRetryMessagesOnSecondAttempt() {
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(successResult("plain text"))
                .willReturn(successResult(validJson()));

        service.generate(context());

        verify(invoker, times(2)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void shouldCompactRetryWhenFirstResponseIsLengthLimited() {
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(ModelChatResult.success("{}", "length", 1L, 8192L, 8200L, 100L, "deepseek", "deepseek-chat"))
                .willReturn(successResult(validJson()));

        GeneratedProject project = service.generate(context());

        assertThat(project.files()).hasSize(1);
        verify(invoker, times(2)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
    }

    private ModelChatResult successResult(String content) {
        return ModelChatResult.success(content, "stop", 10L, 20L, 30L, 100L, "deepseek", "deepseek-chat");
    }

    private String validJson() {
        return """
                {"projectName":"待办","description":"d","files":[{"path":"index.html","content":"<!doctype html><html lang=\\"zh-CN\\"><head><meta charset=\\"UTF-8\\"><title>待办</title></head><body></body></html>"}]}
                """;
    }

    private GenerationContext context() {
        return new GenerationContext(
                "生成一个待办清单页面",
                "今日任务",
                "WEB_APP",
                "HTML",
                1L, 2L, 3L,
                null, null, null, null, null, null
        );
    }
}
