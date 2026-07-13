package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
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
class ProviderAutoRoutingTest {

    @Mock
    private ModelProviderRoutingConfigService routingConfigService;

    @Mock
    private AiRoutingConfigService aiRoutingConfigService;

    private ProviderCandidateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProviderCandidateResolver(routingConfigService, aiRoutingConfigService);
    }

    @Test
    void autoModeUsesAllEligibleProvidersTest() {
        setProviderConfig("auto");
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity openai = aiProvider(2L, "openai", 20);
        ModelProviderEntity qwen = aiProvider(3L, "qwen", 30);
        stubConfiguredProviders(List.of(deepseek, openai, qwen));

        assertThat(resolver.resolveAiProviders())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek", "openai", "qwen");
    }

    @Test
    void autoModeSkipsMissingKeyProviderTest() {
        setProviderConfig("auto");
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity openai = aiProvider(2L, "openai", 20);
        ModelProviderEntity qwen = aiProvider(3L, "qwen", 30);
        given(routingConfigService.listActiveProvidersSorted())
                .willReturn(List.of(deepseek, openai, qwen));
        given(routingConfigService.isRuleProvider(deepseek)).willReturn(false);
        given(routingConfigService.isRuleProvider(openai)).willReturn(false);
        given(routingConfigService.isRuleProvider(qwen)).willReturn(false);
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);
        given(routingConfigService.isRuntimeConfigured(openai)).willReturn(false);
        given(routingConfigService.isRuntimeConfigured(qwen)).willReturn(true);

        assertThat(resolver.resolveAiProviders())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek", "qwen");
    }

    @Test
    void autoModeOrdersByPriorityThenIdTest() {
        setProviderConfig("auto");
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity openai = aiProvider(2L, "openai", 20);
        ModelProviderEntity qwen = aiProvider(3L, "qwen", 30);
        given(routingConfigService.listActiveProvidersSorted())
                .willReturn(List.of(deepseek, openai, qwen));
        given(routingConfigService.isRuleProvider(org.mockito.ArgumentMatchers.any())).willReturn(false);
        given(routingConfigService.isRuntimeConfigured(org.mockito.ArgumentMatchers.any())).willReturn(true);

        assertThat(resolver.resolveAiProviders())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek", "openai", "qwen");
    }

    @Test
    void pinnedModeUsesOnlySelectedProviderTest() {
        setProviderConfig("deepseek");
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity openai = aiProvider(2L, "openai", 20);
        stubConfiguredProviders(List.of(deepseek, openai));

        assertThat(resolver.resolveAiProviders())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek");
    }

    @Test
    void pinnedMissingProviderDoesNotSilentlyUseOtherAiProviderTest() {
        setProviderConfig("openai");
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity openai = aiProvider(2L, "openai", 20);
        given(routingConfigService.listActiveProvidersSorted())
                .willReturn(List.of(deepseek, openai));
        given(routingConfigService.isRuleProvider(deepseek)).willReturn(false);
        given(routingConfigService.isRuleProvider(openai)).willReturn(false);
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);
        given(routingConfigService.isRuntimeConfigured(openai)).willReturn(false);

        assertThat(resolver.resolveAiProviders()).isEmpty();
        assertThat(resolver.hasConfiguredAiProvider()).isFalse();
    }

    @Test
    void ruleIsNotPartOfLlmCandidateListTest() {
        setProviderConfig("auto");
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity rule = ruleProvider(99L);
        given(routingConfigService.listActiveProvidersSorted())
                .willReturn(List.of(deepseek, rule));
        given(routingConfigService.isRuleProvider(deepseek)).willReturn(false);
        given(routingConfigService.isRuleProvider(rule)).willReturn(true);
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);

        assertThat(resolver.resolveAiProviders())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek");
        assertThat(resolver.resolveAvailableProviders())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek", "rule");
    }

    @Test
    void adminPriorityChangeRefreshesCandidateOrderAfterCacheInvalidation() {
        ModelProviderEntityMapper providerMapper = org.mockito.Mockito.mock(ModelProviderEntityMapper.class);
        ProviderCredentialResolver credentialResolver = ProviderRoutingTestSupport.lenientConfiguredResolver();
        ModelProviderRoutingConfigService liveRouting = new ModelProviderRoutingConfigService(providerMapper, credentialResolver);
        AiRoutingConfigService liveAiRouting = org.mockito.Mockito.mock(AiRoutingConfigService.class);
        ProviderRoutingTestSupport.stubAutoRouting(liveAiRouting);
        ProviderCandidateResolver liveResolver = new ProviderCandidateResolver(liveRouting, liveAiRouting);

        ModelProviderEntity deepseekFirst = aiProviderWithEnv(1L, "deepseek", 10, "PATH");
        ModelProviderEntity openaiSecond = aiProviderWithEnv(2L, "openai", 20, "PATH");
        ModelProviderEntity deepseekLater = aiProviderWithEnv(1L, "deepseek", 20, "PATH");
        ModelProviderEntity openaiLater = aiProviderWithEnv(2L, "openai", 10, "PATH");
        given(providerMapper.findAll())
                .willReturn(List.of(deepseekFirst, openaiSecond))
                .willReturn(List.of(deepseekLater, openaiLater));

        assertThat(liveResolver.resolveAiProviders())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek", "openai");

        liveRouting.invalidateCache();

        assertThat(liveResolver.resolveAiProviders())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("openai", "deepseek");
    }

    @Test
    void lengthUsesCompactRetryBeforeCrossProviderFailoverTest() {
        setProviderConfig("auto");
        assertThat(ProviderRoutingMode.fromConfigValue("auto")).isEqualTo(ProviderRoutingMode.AUTO);
        assertThat(resolver.routingMode()).isEqualTo(ProviderRoutingMode.AUTO);
    }

    @Test
    void blankProviderConfigDefaultsToAutoMode() {
        setProviderConfig("  ");
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10);
        ModelProviderEntity openai = aiProvider(2L, "openai", 20);
        stubConfiguredProviders(List.of(deepseek, openai));

        assertThat(resolver.routingMode()).isEqualTo(ProviderRoutingMode.AUTO);
        assertThat(resolver.resolveAiProviders()).hasSize(2);
    }

    private void setProviderConfig(String value) {
        ProviderRoutingTestSupport.stubPinnedRouting(aiRoutingConfigService, value);
    }

    private void stubConfiguredProviders(List<ModelProviderEntity> providers) {
        given(routingConfigService.listActiveProvidersSorted()).willReturn(providers);
        for (ModelProviderEntity provider : providers) {
            given(routingConfigService.isRuleProvider(provider)).willReturn("rule".equalsIgnoreCase(provider.getProviderCode()));
            given(routingConfigService.isRuntimeConfigured(provider)).willReturn(true);
        }
    }

    private ModelProviderEntity aiProviderWithEnv(Long id, String code, int priority, String apiKeyEnv) {
        return ModelProviderEntity.builder()
                .id(id)
                .providerCode(code)
                .providerName(code)
                .baseUrl("https://api.example.com")
                .authMode("API_KEY")
                .apiKeyEnv(apiKeyEnv)
                .apiProtocol("OPENAI_COMPATIBLE")
                .defaultModel("model")
                .priority(priority)
                .status("ACTIVE")
                .isDeleted(0)
                .build();
    }

    private ModelProviderEntity aiProvider(Long id, String code, int priority) {
        return ModelProviderEntity.builder()
                .id(id)
                .providerCode(code)
                .providerName(code)
                .baseUrl("https://api.example.com")
                .authMode("API_KEY")
                .apiKeyEnv(code.toUpperCase() + "_API_KEY")
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
