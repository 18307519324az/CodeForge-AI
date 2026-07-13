package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.GeneratedProject;
import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.model.ModelChatRequest;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayFactory;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.generation.model.ProviderCredentialResolver;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser;
import com.codeforge.ai.domain.generation.progress.ModelGenerationProgress;
import com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactValidator;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTrace;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.infrastructure.ai.OpenAiCompatibleModelGateway;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AiDirectStreamingProgressIntegrationTest {

    @Mock private ModelProviderSelector selector;
    @Mock private ModelGatewayFactory factory;
    @Mock private ModelCallLogEntityMapper callLogMapper;
    @Mock private PromptTemplateTraceResolver promptTemplateTraceResolver;
    @Mock private OpenAiCompatibleModelGateway openAiGateway;
    @Mock private ProviderCredentialResolver credentialResolver;

    private ModelGatewayInvoker invoker;
    private CodeGenerationAiService aiService;
    private List<ModelGenerationProgress> progressEvents;

    @BeforeEach
    void setUp() {
        lenient().when(promptTemplateTraceResolver.resolveByTaskId(any())).thenReturn(PromptTemplateTrace.empty());
        lenient().when(callLogMapper.insertCallLog(any())).thenReturn(1);
        invoker = new ModelGatewayInvoker(selector, factory, callLogMapper, promptTemplateTraceResolver, credentialResolver);
        aiService = new CodeGenerationAiService(
                invoker,
                new AiGeneratedProjectParser(),
                new GeneratedArtifactValidator(),
                new PromptResourceLoader());
        progressEvents = new ArrayList<>();
    }

    @Test
    void emitsSafeProgressWithoutRawContent() {
        stubStreamingProvider("deepseek", streamInChunks(validProjectJson(), "stop", 1));

        aiService.generate(context(), captureProgress());

        assertThat(progressEvents).isNotEmpty();
        assertThat(progressEvents.getFirst().attempt()).isEqualTo(1);
        assertThat(progressEvents.getLast().receivedChars()).isEqualTo(validProjectJson().length());
        for (ModelGenerationProgress progress : progressEvents) {
            assertThat(progress.receivedChars()).isGreaterThan(0);
            assertThat(progress.chunkCount()).isGreaterThan(0);
        }
    }

    @Test
    void finishReasonLengthTriggersCompactRetryWithAttemptTwo() {
        String validJson = validProjectJson();
        AtomicInteger call = new AtomicInteger();
        given(selector.selectAiProviders()).willReturn(List.of(aiProvider("deepseek")));
        given(factory.getGateway(any(ModelProviderEntity.class))).willReturn(openAiGateway);
        doAnswer(invocation -> {
                    ModelStreamHandler handler = invocation.getArgument(1);
                    if (call.getAndIncrement() == 0) {
                        simulateStream(handler, "{}", "length", 1);
                    } else {
                        simulateStream(handler, validJson, "stop", 2);
                    }
                    return null;
                }).when(openAiGateway).streamChatRequest(any(ModelChatRequest.class), any(ModelStreamHandler.class));

        GeneratedProject project = aiService.generate(context(), captureProgress());

        assertThat(project.files()).hasSize(1);
        assertThat(progressEvents.stream().map(ModelGenerationProgress::attempt)).contains(1, 2);
        assertThat(progressEvents.stream().filter(progress -> progress.attempt() == 2).findFirst())
                .isPresent()
                .get()
                .satisfies(progress -> assertThat(progress.receivedChars()).isLessThan(validJson.length() + 100));
    }

    @Test
    void partialStreamFailureDoesNotProduceSuccessProject() {
        given(selector.selectAiProviders()).willReturn(List.of(aiProvider("deepseek")));
        given(factory.getGateway(any(ModelProviderEntity.class))).willReturn(openAiGateway);
        doAnswer(invocation -> {
                    ModelStreamHandler handler = invocation.getArgument(1);
                    handler.onStart();
                    handler.onDelta("x".repeat(8000));
                    handler.onError(new RuntimeException("network disconnected"));
                    return null;
                }).when(openAiGateway).streamChatRequest(any(ModelChatRequest.class), any(ModelStreamHandler.class));

        assertThatThrownBy(() -> aiService.generate(context(), captureProgress()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void providerFallbackResetsProgressAttempt() {
        ModelProviderEntity first = aiProvider("provider-a");
        ModelProviderEntity second = aiProvider("provider-b");
        given(selector.selectAiProviders()).willReturn(List.of(first, second));
        given(factory.getGateway(first)).willReturn(openAiGateway);
        given(factory.getGateway(second)).willReturn(openAiGateway);

        AtomicInteger call = new AtomicInteger();
        doAnswer(invocation -> {
                    ModelStreamHandler handler = invocation.getArgument(1);
                    if (call.getAndIncrement() == 0) {
                        handler.onStart();
                        handler.onDelta("partial-a");
                        handler.onError(new RuntimeException("provider-a failed"));
                    } else {
                        simulateStream(handler, validProjectJson(), "stop", 2);
                    }
                    return null;
                }).when(openAiGateway).streamChatRequest(any(ModelChatRequest.class), any(ModelStreamHandler.class));

        GeneratedProject project = aiService.generate(context(), captureProgress());

        assertThat(project.files()).hasSize(1);
        assertThat(progressEvents.stream().map(ModelGenerationProgress::attempt)).contains(1, 2);
        assertThat(progressEvents.stream().noneMatch(progress -> progress.receivedChars() > validProjectJson().length()))
                .isTrue();
    }

    private ModelGenerationProgressListener captureProgress() {
        return progressEvents::add;
    }

    private void stubStreamingProvider(String code, StreamingSimulation simulation) {
        given(selector.selectAiProviders()).willReturn(List.of(aiProvider(code)));
        given(factory.getGateway(any(ModelProviderEntity.class))).willReturn(openAiGateway);
        doAnswer(invocation -> {
                    simulateStream(invocation.getArgument(1), simulation.content(), simulation.finishReason(), simulation.attempt());
                    return null;
                }).when(openAiGateway).streamChatRequest(any(ModelChatRequest.class), any(ModelStreamHandler.class));
    }

    private StreamingSimulation streamInChunks(String content, String finishReason, int attempt) {
        return new StreamingSimulation(content, finishReason, attempt);
    }

    private void simulateStream(ModelStreamHandler handler, String content, String finishReason, int attempt) {
        handler.onStart();
        int chunkSize = Math.max(1, content.length() / 5);
        for (int index = 0; index < content.length(); index += chunkSize) {
            handler.onDelta(content.substring(index, Math.min(content.length(), index + chunkSize)));
        }
        handler.onComplete(ModelChatResult.success(
                content,
                finishReason,
                100L,
                200L,
                300L,
                50L,
                "deepseek",
                "deepseek-chat"));
    }

    private ModelProviderEntity aiProvider(String code) {
        return ModelProviderEntity.builder()
                .id(1L)
                .providerCode(code)
                .apiProtocol("OPENAI_COMPATIBLE")
                .apiKeyEnv("DEEPSEEK_API_KEY")
                .defaultModel("deepseek-chat")
                .baseUrl("https://api.deepseek.com/v1")
                .build();
    }

    private GenerationContext context() {
        return new GenerationContext(
                "生成一个待办清单页面",
                "今日任务",
                "WEB_APP",
                "HTML",
                1L, 2L, 3L,
                null, null, null, null, null, null);
    }

    private String validProjectJson() {
        return """
                {"projectName":"待办","description":"d","files":[{"path":"index.html","content":"<!doctype html><html lang=\\"zh-CN\\"><head><meta charset=\\"UTF-8\\"><title>待办</title></head><body></body></html>"}]}
                """;
    }

    private record StreamingSimulation(String content, String finishReason, int attempt) {
    }
}
