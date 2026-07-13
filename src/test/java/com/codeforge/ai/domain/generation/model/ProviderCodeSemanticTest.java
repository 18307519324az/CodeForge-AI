package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProviderCodeSemanticTest {

    @Mock
    private ModelProviderEntityMapper providerMapper;

    @Mock
    private ProviderCredentialResolver credentialResolver;

    private ModelProviderSelector selector;

    @BeforeEach
    void setUp() {
        ModelProviderRoutingConfigService routingConfigService =
                new ModelProviderRoutingConfigService(providerMapper, credentialResolver);
        AiRoutingConfigService aiRoutingConfigService = org.mockito.Mockito.mock(AiRoutingConfigService.class);
        ProviderRoutingTestSupport.stubPinnedRouting(aiRoutingConfigService, "deepseek");
        ProviderCandidateResolver candidateResolver =
                new ProviderCandidateResolver(routingConfigService, aiRoutingConfigService);
        selector = new ModelProviderSelector(candidateResolver);
        given(credentialResolver.isConfigured(org.mockito.ArgumentMatchers.any(ModelProviderEntity.class)))
                .willReturn(true);
    }

    @Test
    void shouldPreferConfiguredDeepseekProviderWhenMultipleAiProvidersExist() {
        given(providerMapper.findAll()).willReturn(List.of(
                provider(1L, "openai", "PATH"),
                provider(2L, "deepseek", "PATH"),
                ruleProvider()
        ));

        List<ModelProviderEntity> providers = selector.selectAiProviders();

        assertThat(providers).extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek");
    }

    @Test
    void shouldReturnEmptyAiProvidersWhenPinnedProviderMissing() {
        given(providerMapper.findAll()).willReturn(List.of(
                provider(1L, "openai", "PATH"),
                ruleProvider()
        ));

        List<ModelProviderEntity> providers = selector.selectAiProviders();

        assertThat(providers).isEmpty();
    }

    @Test
    void shouldKeepRuleProviderOnlyInAvailableListTail() {
        given(providerMapper.findAll()).willReturn(List.of(
                provider(1L, "openai", "PATH"),
                provider(2L, "deepseek", "PATH"),
                ruleProvider()
        ));

        List<ModelProviderEntity> providers = selector.selectAvailable();

        assertThat(providers).extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek", "rule");
    }

    private ModelProviderEntity provider(Long id, String code, String apiKeyEnv) {
        return ModelProviderEntity.builder()
                .id(id)
                .providerCode(code)
                .providerName(code)
                .apiProtocol("OPENAI_COMPATIBLE")
                .authMode("API_KEY")
                .apiKeyEnv(apiKeyEnv)
                .defaultModel("deepseek".equals(code) ? "deepseek-chat" : "gpt-4.1-mini")
                .status("ACTIVE")
                .priority(id.intValue())
                .isDeleted(0)
                .build();
    }

    private ModelProviderEntity ruleProvider() {
        return ModelProviderEntity.builder()
                .id(99L)
                .providerCode("rule")
                .providerName("rule")
                .apiProtocol("RULE_BASED")
                .authMode("NONE")
                .status("ACTIVE")
                .priority(999)
                .isDeleted(0)
                .build();
    }
}
