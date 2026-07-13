package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.generation.validation.ArtifactValidationResult;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiDirectPromptContractTest {

    @Mock
    private ModelGatewayInvoker invoker;

    @Mock
    private AiGeneratedProjectParser parser;

    @Mock
    private GeneratedArtifactValidator artifactValidator;

    @Mock
    private PromptResourceLoader promptLoader;

    @InjectMocks
    private CodeGenerationAiService service;

    @BeforeEach
    void setUp() {
        given(artifactValidator.validate(any(), anyString())).willReturn(ArtifactValidationResult.valid());
    }

    @Test
    void shouldBuildPromptWithAppContextAndBusinessStructure() {
        GenerationContext context = context();
        given(promptLoader.load("codegen-html-system-prompt.txt")).willReturn("JSON only");
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any())).willReturn(
                ModelChatResult.success("{\"files\":[{\"path\":\"index.html\",\"content\":\"<!doctype html><html></html>\"}]}",
                        "stop", 10L, 20L, 30L, 100L, "deepseek", "deepseek-chat"));
        given(parser.parse(any(), any())).willReturn(sampleProject());

        service.generate(context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModelMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(invoker).streamWithAiProvidersOnly(captor.capture(), any(), any(), any(Integer.class), any());
        List<ModelMessage> messages = captor.getValue();

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("JSON only");
        assertThat(messages.get(1).content()).contains("Role");
        assertThat(messages.get(1).content()).contains("Business Context");
        assertThat(messages.get(1).content()).contains("App Type: WEB_APP");
        assertThat(messages.get(1).content()).contains("Business Modules");
        assertThat(messages.get(1).content()).contains("Business Workflow");
        assertThat(messages.get(1).content()).contains("Business Interaction");
        assertThat(messages.get(1).content()).contains("Output JSON");
        assertThat(messages.get(1).content()).contains("Follow the system prompt schema exactly");
    }

    @Test
    void shouldCompactRetryWhenLengthFinishReasonBeforeParsing() {
        given(promptLoader.load("codegen-html-system-prompt.txt")).willReturn("JSON only");
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(ModelChatResult.success("{\"files\":[]}", "length", 1L, 8192L, 8193L, 100L, "deepseek", "deepseek-chat"))
                .willReturn(ModelChatResult.success("{\"files\":[{\"path\":\"index.html\",\"content\":\"<!doctype html><html></html>\"}]}",
                        "stop", 1L, 100L, 101L, 100L, "deepseek", "deepseek-chat"));
        given(parser.parse(any(), any())).willReturn(sampleProject());

        GeneratedProject project = service.generate(context());

        assertThat(project.files()).isNotEmpty();
        verify(invoker, times(2)).streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any());
        verify(parser).parse(any(), any());
    }

    @Test
    void shouldFailWithAiOutputTruncatedWhenCompactRetryStillLengthLimited() {
        given(promptLoader.load("codegen-html-system-prompt.txt")).willReturn("JSON only");
        given(invoker.streamWithAiProvidersOnly(any(), any(), any(), any(Integer.class), any()))
                .willReturn(ModelChatResult.success("{\"files\":[]}", "length", 1L, 8192L, 8193L, 100L, "deepseek", "deepseek-chat"))
                .willReturn(ModelChatResult.success("{\"files\":[]}", "length", 1L, 8192L, 8193L, 100L, "deepseek", "deepseek-chat"));

        assertThatThrownBy(() -> service.generate(context()))
                .isInstanceOf(AiGenerationFailureException.class)
                .extracting(error -> ((AiGenerationFailureException) error).errorCode())
                .isEqualTo(AiGenerationFailureException.AI_OUTPUT_TRUNCATED);

        verify(parser, org.mockito.Mockito.never()).parse(any(), any());
    }

    private GenerationContext context() {
        return new GenerationContext(
                "生成一个待办清单页面",
                "今日任务",
                "WEB_APP",
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

    private GeneratedProject sampleProject() {
        return new GeneratedProject("今日任务", "今日任务", "WEB_APP", "生成一个待办清单页面",
                List.of(new GeneratedProjectFile("index.html", "index.html",
                        "<!doctype html><html lang=\"zh-CN\"><head></head><body></body></html>")));
    }
}
