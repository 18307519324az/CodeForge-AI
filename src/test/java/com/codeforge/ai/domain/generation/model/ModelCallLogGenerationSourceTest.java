package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.ModelCallPhase;
import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.GenerationSource;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTrace;
import com.codeforge.ai.domain.prompt.model.PromptTemplateTraceResolver;
import com.codeforge.ai.domain.generation.RuleBasedModelGateway;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ModelCallLogGenerationSourceTest {

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
        lenient().when(promptTemplateTraceResolver.resolveByTaskId(any())).thenReturn(PromptTemplateTrace.empty());
        lenient().when(credentialResolver.resolveApiKey(any())).thenReturn("test-api-key");
        context = new GenerationContext(
                "生成待办页面", "Todo", "WEB_APP", "HTML",
                3001L, 2001L, 6001L, null,
                null, null, null, null, "system prompt");
    }

    @Test
    void shouldRecordAiDirectWhenOpenAiSucceeds() {
        ModelProviderEntity openai = aiProvider("openai");
        given(selector.selectAiProviders()).willReturn(List.of(openai));
        given(factory.getGateway(openai)).willReturn(openAiGateway);
        doAnswer(invocation -> {
                    ModelStreamHandler handler = invocation.getArgument(1);
                    handler.onComplete(ModelChatResult.success(
                            "{\"files\":[]}", "stop", 1L, 2L, 3L, 10L, "openai", "gpt-4.1-mini"));
                    return null;
                }).when(openAiGateway).streamChatRequest(any(ModelChatRequest.class), any(ModelStreamHandler.class));

        invoker.chatWithAiProvidersOnly(List.of(ModelMessage.user("hello")), context);

        ArgumentCaptor<ModelCallLogEntity> captor = ArgumentCaptor.forClass(ModelCallLogEntity.class);
        verify(callLogMapper).insertCallLog(captor.capture());
        assertThat(captor.getValue().getGenerationSource()).isEqualTo(ModelCallPhase.INITIAL.generationSourceCode());
        assertThat(captor.getValue().getFallbackUsed()).isFalse();
        assertThat(captor.getValue().getProviderCode()).isEqualTo("openai");
    }

    @Test
    void legacyAiDirectSourceRemainsCompatibleWithPhaseSpecificCodes() {
        assertThat(ModelCallPhase.INITIAL.generationSourceCode()).isEqualTo("AI_DIRECT_INITIAL");
        assertThat(ModelCallPhase.COMPACT_RETRY.generationSourceCode()).isEqualTo("AI_DIRECT_COMPACT_RETRY");
        assertThat(GenerationSource.AI_DIRECT.code()).isEqualTo("AI_DIRECT");
        assertThat(ModelCallPhase.INITIAL.generationSourceCode()).isNotEqualTo(GenerationSource.AI_DIRECT.code());
    }

    @Test
    void shouldRecordRuleFallbackWhenAiFailsThenRuleSucceeds() {
        ModelProviderEntity openai = aiProvider("openai");
        ModelProviderEntity rule = ruleProvider();
        given(selector.selectAvailable()).willReturn(List.of(openai, rule));
        given(factory.getGateway(openai)).willReturn(openAiGateway);
        given(factory.getGateway(rule)).willReturn(ruleGateway);
        given(openAiGateway.chat(any(ModelChatRequest.class))).willThrow(new RuntimeException("HTTP 401"));
        given(ruleGateway.generate(any(GenerationContext.class))).willReturn("FILE:index.html\n<html></html>\n---END---\n");

        invoker.chatWithFallback(List.of(ModelMessage.user("hello")), context);

        ArgumentCaptor<ModelCallLogEntity> captor = ArgumentCaptor.forClass(ModelCallLogEntity.class);
        verify(callLogMapper, org.mockito.Mockito.atLeastOnce()).insertCallLog(captor.capture());
        ModelCallLogEntity successLog = captor.getAllValues().stream()
                .filter(log -> "SUCCESS".equals(log.getStatus()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(successLog.getGenerationSource()).isEqualTo(GenerationSource.RULE_FALLBACK.code());
        assertThat(successLog.getFallbackUsed()).isTrue();
    }

    @Test
    void shouldRejectAiDirectWhenNoApiKeyConfigured() {
        given(selector.selectAiProviders()).willReturn(List.of());

        assertThatThrownBy(() -> invoker.chatWithAiProvidersOnly(List.of(ModelMessage.user("hello")), context))
                .isInstanceOf(NoAiProviderAvailableException.class);
    }

    private ModelProviderEntity aiProvider(String code) {
        return ModelProviderEntity.builder()
                .id(1L)
                .providerCode(code)
                .apiProtocol("OPENAI_COMPATIBLE")
                .apiKeyEnv("OPENAI_API_KEY")
                .defaultModel("gpt-4.1-mini")
                .baseUrl("https://api.openai.com/v1")
                .build();
    }

    private ModelProviderEntity ruleProvider() {
        return ModelProviderEntity.builder()
                .id(2L)
                .providerCode("rule")
                .apiProtocol("RULE_BASED")
                .defaultModel("rule-based")
                .authMode("NONE")
                .build();
    }
}
