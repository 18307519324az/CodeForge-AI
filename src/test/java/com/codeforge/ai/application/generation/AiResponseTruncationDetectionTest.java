package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.generation.validation.ArtifactValidationResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiResponseTruncationDetectionTest {

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
    void shouldMapLengthFinishReasonToAiOutputTruncatedAfterCompactRetry() {
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(truncatedResult("deepseek", "deepseek-chat"))
                .willReturn(truncatedResult("deepseek", "deepseek-chat"));

        assertThatThrownBy(() -> service.generate(context()))
                .isInstanceOf(AiGenerationFailureException.class)
                .satisfies(error -> {
                    AiGenerationFailureException failure = (AiGenerationFailureException) error;
                    assertThat(failure.errorCode()).isEqualTo(AiGenerationFailureException.AI_OUTPUT_TRUNCATED);
                    assertThat(failure.userMessage()).contains("长度限制");
                    assertThat(failure.metadata()).containsEntry("finishReason", "length");
                    assertThat(failure.metadata()).containsEntry("configuredMaxTokens", 8192);
                });

        verify(invoker, times(2)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
    }

    private ModelChatResult truncatedResult(String provider, String model) {
        return ModelChatResult.success("{\"projectName\":\"x\"", "length", 100L, 8192L, 8292L, 100L, provider, model);
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
