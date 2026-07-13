package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProviderCandidateResolverTest {

    @Mock
    private ModelProviderRoutingConfigService routingConfigService;

    @Mock
    private AiRoutingConfigService aiRoutingConfigService;

    private ProviderCandidateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProviderCandidateResolver(routingConfigService, aiRoutingConfigService);
        ProviderRoutingTestSupport.stubPinnedRouting(aiRoutingConfigService, "deepseek");
    }

    @Test
    void shouldPreferConfiguredProviderWhenAvailable() {
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity openai = aiProvider(2L, "openai", 5);
        given(routingConfigService.listActiveProvidersSorted()).willReturn(List.of(deepseek, openai));
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);
        given(routingConfigService.isRuntimeConfigured(openai)).willReturn(true);

        assertThat(resolver.resolveAiProviders()).containsExactly(deepseek);
    }

    @Test
    void shouldReturnEmptyWhenPinnedProviderMissingFromConfiguredList() {
        ModelProviderEntity openai = aiProvider(2L, "openai", 5);
        given(routingConfigService.listActiveProvidersSorted()).willReturn(List.of(openai));
        given(routingConfigService.isRuntimeConfigured(openai)).willReturn(true);
        given(routingConfigService.isRuleProvider(openai)).willReturn(false);

        assertThat(resolver.resolveAiProviders()).isEmpty();
    }

    @Test
    void shouldAppendRuleProviderAfterAiProviders() {
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity rule = ruleProvider(3L);
        given(routingConfigService.listActiveProvidersSorted()).willReturn(List.of(deepseek, rule));
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);
        given(routingConfigService.isRuleProvider(deepseek)).willReturn(false);
        given(routingConfigService.isRuleProvider(rule)).willReturn(true);

        assertThat(resolver.resolveAvailableProviders()).containsExactly(deepseek, rule);
    }

    @Test
    void shouldSkipUnconfiguredProvidersFromAiCandidates() {
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity openai = aiProvider(2L, "openai", 20);
        given(routingConfigService.listActiveProvidersSorted()).willReturn(List.of(deepseek, openai));
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);
        given(routingConfigService.isRuntimeConfigured(openai)).willReturn(false);

        assertThat(resolver.resolveAiProviders()).containsExactly(deepseek);
    }

    private ModelProviderEntity aiProvider(Long id, String code, int priority) {
        return ModelProviderEntity.builder()
                .id(id)
                .providerCode(code)
                .providerName(code)
                .baseUrl("https://api.example.com")
                .authMode("API_KEY")
                .apiKeyEnv("OPENAI_API_KEY")
                .apiProtocol("OPENAI_COMPATIBLE")
                .defaultModel("model")
                .priority(priority)
                .status("ACTIVE")
                .build();
    }

    private ModelProviderEntity ruleProvider(Long id) {
        return ModelProviderEntity.builder()
                .id(id)
                .providerCode("rule")
                .providerName("Rule")
                .authMode("NONE")
                .apiProtocol("RULE_BASED")
                .defaultModel("rule-based")
                .priority(999)
                .status("ACTIVE")
                .build();
    }
}
