package com.codeforge.ai.application.service;

import com.codeforge.ai.application.generation.AiCodegenPromptBuilder;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.ModelCallPhase;
import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelGatewayInvoker;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.generation.model.ModelProviderSelector;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.prompt.model.PromptFingerprintHasher;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTrace;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.infrastructure.ai.OpenAiCompatibleModelGateway;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptRetryTraceTest {

    @Mock private ModelProviderSelector selector;
    @Mock private com.codeforge.ai.domain.generation.model.ModelGatewayFactory factory;
    @Mock private ModelCallLogEntityMapper callLogMapper;
    @Mock private PromptTemplateTraceResolver promptTemplateTraceResolver;
    @Mock private com.codeforge.ai.domain.generation.model.ProviderCredentialResolver credentialResolver;
    @Mock private OpenAiCompatibleModelGateway openAiGateway;

    @InjectMocks
    private ModelGatewayInvoker invoker;

    private GenerationContext context;

    @BeforeEach
    void setUp() {
        context = new GenerationContext(
                "hello", "App", "WEB_APP", "HTML",
                3001L, 2001L, 6001L, null,
                null, null, null, null,
                "system-v1",
                "user-v1",
                4001L, 5001L, "trace-template", 1);
        given(selector.selectAiProviders()).willReturn(List.of(aiProvider()));
        given(factory.getGateway(any())).willReturn(openAiGateway);
        lenient().when(credentialResolver.resolveApiKey(any())).thenReturn("test-api-key");
        doAnswer(invocation -> {
            ModelStreamHandler handler = invocation.getArgument(1);
            handler.onComplete(ModelChatResult.success(
                    "{\"files\":[]}", "stop", 1L, 2L, 3L, 10L, "openai", "gpt-4.1-mini"));
            return null;
        }).when(openAiGateway).streamChatRequest(any(), any());
    }

    @Test
    void retryPersistsTemplateIdentityForEveryCall() {
        List<ModelMessage> initial = AiCodegenPromptBuilder.buildInitialMessages("system-v1", context);
        invoker.streamWithAiProvidersOnly(initial, context, com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener.NOOP,
                1, ModelCallPhase.INITIAL);
        List<ModelMessage> retry = AiCodegenPromptBuilder.buildRetryMessages("system-v1", context);
        invoker.streamWithAiProvidersOnly(retry, context, com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener.NOOP,
                2, ModelCallPhase.PARSE_RETRY);

        ArgumentCaptor<ModelCallLogEntity> captor = ArgumentCaptor.forClass(ModelCallLogEntity.class);
        verify(callLogMapper, times(2)).insertCallLog(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(log -> {
            assertThat(log.getPromptTemplateVersionId()).isEqualTo(5001L);
            assertThat(log.getPromptTemplateVersionNo()).isEqualTo(1);
            assertThat(log.getPromptTemplateCode()).isEqualTo("trace-template");
        });
    }

    @Test
    void compactRetryHashesActualRetryPrompt() {
        List<ModelMessage> compact = AiCodegenPromptBuilder.buildCompactMessages("system-v1", context);
        invoker.streamWithAiProvidersOnly(compact, context, com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener.NOOP,
                2, ModelCallPhase.COMPACT_RETRY);

        ArgumentCaptor<ModelCallLogEntity> captor = ArgumentCaptor.forClass(ModelCallLogEntity.class);
        verify(callLogMapper).insertCallLog(captor.capture());
        var expected = PromptFingerprintHasher.fromMessages(compact);
        assertThat(captor.getValue().getCombinedPromptFingerprint()).isEqualTo(expected.combined());
        assertThat(captor.getValue().getUserPromptSha256()).isEqualTo(expected.userSha256());
    }

    @Test
    void providerFallbackPreservesPromptTrace() {
        ModelProviderEntity fallbackProvider = ModelProviderEntity.builder()
                .id(2L)
                .providerCode("deepseek")
                .apiProtocol("OPENAI_COMPATIBLE")
                .defaultModel("deepseek-chat")
                .build();
        given(selector.selectAiProviders()).willReturn(List.of(aiProvider(), fallbackProvider));
        given(factory.getGateway(any())).willAnswer(invocation -> {
            ModelProviderEntity provider = invocation.getArgument(0);
            if ("openai".equals(provider.getProviderCode())) {
                throw new RuntimeException("primary failed");
            }
            return openAiGateway;
        });
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildInitialMessages("system-v1", context);

        try {
            invoker.streamWithAiProvidersOnly(messages, context,
                    com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener.NOOP,
                    1, ModelCallPhase.INITIAL);
        } catch (RuntimeException ignored) {
            // second provider should succeed
        }

        ArgumentCaptor<ModelCallLogEntity> captor = ArgumentCaptor.forClass(ModelCallLogEntity.class);
        verify(callLogMapper, times(2)).insertCallLog(captor.capture());
        var expected = PromptFingerprintHasher.fromMessages(messages);
        assertThat(captor.getAllValues().get(0).getCombinedPromptFingerprint()).isEqualTo(expected.combined());
        assertThat(captor.getAllValues().get(1).getCombinedPromptFingerprint()).isEqualTo(expected.combined());
        assertThat(captor.getAllValues().get(0).getPromptTemplateVersionId()).isEqualTo(5001L);
        assertThat(captor.getAllValues().get(1).getPromptTemplateVersionId()).isEqualTo(5001L);
    }

    @Test
    void failedCallStillPersistsPromptTrace() {
        given(factory.getGateway(any())).willThrow(new RuntimeException("provider failed"));
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildInitialMessages("system-v1", context);

        try {
            invoker.streamWithAiProvidersOnly(messages, context,
                    com.codeforge.ai.domain.generation.progress.ModelGenerationProgressListener.NOOP,
                    1, ModelCallPhase.INITIAL);
        } catch (RuntimeException ignored) {
        }

        ArgumentCaptor<ModelCallLogEntity> captor = ArgumentCaptor.forClass(ModelCallLogEntity.class);
        verify(callLogMapper).insertCallLog(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getPromptTemplateVersionId()).isEqualTo(5001L);
        assertThat(captor.getValue().getCombinedPromptFingerprint()).isNotBlank();
    }

    private ModelProviderEntity aiProvider() {
        return ModelProviderEntity.builder()
                .id(1L)
                .providerCode("openai")
                .apiProtocol("OPENAI_COMPATIBLE")
                .defaultModel("gpt-4.1-mini")
                .build();
    }
}
