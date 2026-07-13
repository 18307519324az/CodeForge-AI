package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.RuleBasedModelGateway;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTrace;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.infrastructure.ai.OpenAiCompatibleModelGateway;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ModelGatewayInvokerRoutingTest {

    @Mock private ModelProviderSelector selector;
    @Mock private ModelGatewayFactory factory;
    @Mock private ModelCallLogEntityMapper callLogMapper;
    @Mock private PromptTemplateTraceResolver promptTemplateTraceResolver;
    @Mock private ProviderCredentialResolver credentialResolver;
    @Mock private OpenAiCompatibleModelGateway openAiGateway;
    @Mock private RuleBasedModelGateway ruleGateway;

    @InjectMocks
    private ModelGatewayInvoker invoker;

    private GenerationContext context;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(promptTemplateTraceResolver.resolveByTaskId(any()))
                .thenReturn(PromptTemplateTrace.empty());
        org.mockito.Mockito.lenient()
                .when(credentialResolver.resolveApiKey(any()))
                .thenReturn("test-api-key");
        context = new GenerationContext(
                "生成待办页面", "Todo", "WEB_APP", "HTML",
                3001L, 2001L, 6001L, null,
                null, null, null, null, "system prompt");
    }

    @Test
    void transportFailureFallsBackAcrossProvidersInAutoModeTest() {
        ModelProviderEntity deepseek = aiProvider("deepseek");
        ModelProviderEntity openai = aiProvider("openai");
        given(selector.selectAiProviders()).willReturn(List.of(deepseek, openai));
        given(factory.getGateway(deepseek)).willReturn(openAiGateway);
        given(factory.getGateway(openai)).willReturn(openAiGateway);
        doAnswer(invocation -> {
                    ModelStreamHandler handler = invocation.getArgument(1);
                    if ("deepseek".equals(((ModelChatRequest) invocation.getArgument(0)).providerCode())) {
                        handler.onError(new RuntimeException("Connection reset"));
                    } else {
                        handler.onComplete(ModelChatResult.success("ok", "stop", 1L, 2L, 3L, 10L, "openai", "gpt-4.1-mini"));
                    }
                    return null;
                }).when(openAiGateway).streamChatRequest(any(ModelChatRequest.class), any(ModelStreamHandler.class));

        ModelChatResult result = invoker.chatWithAiProvidersOnly(List.of(ModelMessage.user("hello")), context);

        assertThat(result.providerCode()).isEqualTo("openai");
        verify(openAiGateway, org.mockito.Mockito.times(2)).streamChatRequest(any(ModelChatRequest.class), any(ModelStreamHandler.class));
    }

    @Test
    void authFailureFallsBackAcrossProvidersInAutoModeTest() {
        ModelProviderEntity deepseek = aiProvider("deepseek");
        ModelProviderEntity openai = aiProvider("openai");
        given(selector.selectAiProviders()).willReturn(List.of(deepseek, openai));
        given(factory.getGateway(deepseek)).willReturn(openAiGateway);
        given(factory.getGateway(openai)).willReturn(openAiGateway);
        doAnswer(invocation -> {
                    ModelStreamHandler handler = invocation.getArgument(1);
                    if ("deepseek".equals(((ModelChatRequest) invocation.getArgument(0)).providerCode())) {
                        handler.onError(new RuntimeException("HTTP 401"));
                    } else {
                        handler.onComplete(ModelChatResult.success("ok", "stop", 1L, 2L, 3L, 10L, "openai", "gpt-4.1-mini"));
                    }
                    return null;
                }).when(openAiGateway).streamChatRequest(any(ModelChatRequest.class), any(ModelStreamHandler.class));

        ModelChatResult result = invoker.chatWithAiProvidersOnly(List.of(ModelMessage.user("hello")), context);

        assertThat(result.providerCode()).isEqualTo("openai");
    }

    @Test
    void allAiProvidersFailThenRuleFallbackTest() {
        ModelProviderEntity deepseek = aiProvider("deepseek");
        ModelProviderEntity openai = aiProvider("openai");
        ModelProviderEntity rule = ruleProvider();
        given(selector.selectAvailable()).willReturn(List.of(deepseek, openai, rule));
        given(factory.getGateway(deepseek)).willReturn(openAiGateway);
        given(factory.getGateway(openai)).willReturn(openAiGateway);
        given(factory.getGateway(rule)).willReturn(ruleGateway);
        given(openAiGateway.chat(any(ModelChatRequest.class))).willThrow(new RuntimeException("HTTP 503"));
        given(ruleGateway.generate(any(GenerationContext.class))).willReturn("FILE:index.html\n<html></html>\n---END---\n");

        ModelChatResult result = invoker.chatWithFallback(List.of(ModelMessage.user("hello")), context);

        assertThat(result.providerCode()).isEqualTo("rule");
        verify(ruleGateway).generate(any(GenerationContext.class));
    }

    @Test
    void pinnedDeepSeekFailureDoesNotCallOpenAiBeforeRuleFallback() {
        ModelProviderEntity deepseek = aiProvider("deepseek");
        ModelProviderEntity rule = ruleProvider();
        given(selector.selectAvailable()).willReturn(List.of(deepseek, rule));
        given(factory.getGateway(deepseek)).willReturn(openAiGateway);
        given(factory.getGateway(rule)).willReturn(ruleGateway);
        given(openAiGateway.chat(any(ModelChatRequest.class))).willThrow(new RuntimeException("HTTP 503"));
        given(ruleGateway.generate(any(GenerationContext.class))).willReturn("FILE:index.html\n<html></html>\n---END---\n");

        ModelChatResult result = invoker.chatWithFallback(List.of(ModelMessage.user("hello")), context);

        assertThat(result.providerCode()).isEqualTo("rule");
        verify(openAiGateway, org.mockito.Mockito.times(1)).chat(any(ModelChatRequest.class));
    }

    @Test
    void pinnedMissingKeyYieldsNoAiProviderThenRuleFallback() {
        ModelProviderEntity rule = ruleProvider();
        given(selector.selectAiProviders()).willReturn(List.of());
        given(selector.selectAvailable()).willReturn(List.of(rule));
        given(factory.getGateway(rule)).willReturn(ruleGateway);
        given(ruleGateway.generate(any(GenerationContext.class))).willReturn("FILE:index.html\n<html></html>\n---END---\n");

        assertThatThrownBy(() -> invoker.chatWithAiProvidersOnly(List.of(ModelMessage.user("hello")), context))
                .isInstanceOf(NoAiProviderAvailableException.class);

        ModelChatResult fallback = invoker.chatWithFallback(List.of(ModelMessage.user("hello")), context);
        assertThat(fallback.providerCode()).isEqualTo("rule");
        assertThat(fallback.content()).contains("index.html");
    }

    private ModelProviderEntity aiProvider(String code) {
        return ModelProviderEntity.builder()
                .id((long) code.hashCode())
                .providerCode(code)
                .apiProtocol("OPENAI_COMPATIBLE")
                .apiKeyEnv(code.toUpperCase() + "_API_KEY")
                .defaultModel("model")
                .baseUrl("https://api.example.com")
                .build();
    }

    private ModelProviderEntity ruleProvider() {
        return ModelProviderEntity.builder()
                .id(99L)
                .providerCode("rule")
                .apiProtocol("RULE_BASED")
                .defaultModel("rule-based")
                .authMode("NONE")
                .build();
    }
}
