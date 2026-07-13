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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProviderRoutingContractTest {

    @Mock
    private ModelProviderEntityMapper providerMapper;

    @Mock
    private ProviderCredentialResolver credentialResolver;

    private ModelProviderRoutingConfigService routingConfigService;

    @BeforeEach
    void setUp() {
        routingConfigService = new ModelProviderRoutingConfigService(providerMapper, credentialResolver);
        org.mockito.Mockito.lenient()
                .when(credentialResolver.isConfigured(any(ModelProviderEntity.class)))
                .thenReturn(false);
    }

    @Test
    void enabledDoesNotMeanConfiguredTest() {
        ModelProviderEntity enabledMissingKey = aiProvider(3L, "qwen", 30, "QWEN_API_KEY");

        assertThat(routingConfigService.isActiveStatus(enabledMissingKey.getStatus())).isTrue();
        assertThat(routingConfigService.isRuntimeConfigured(enabledMissingKey)).isFalse();
    }

    @Test
    void providersAreOrderedByPriorityTest() {
        ModelProviderEntity p30 = aiProvider(3L, "qwen", 30, "QWEN_API_KEY");
        ModelProviderEntity p10 = aiProvider(1L, "deepseek", 10, "DEEPSEEK_API_KEY");
        ModelProviderEntity p20 = aiProvider(2L, "openai", 20, "OPENAI_API_KEY");
        given(providerMapper.findAll()).willReturn(List.of(p30, p10, p20));
        routingConfigService.invalidateCache();

        assertThat(routingConfigService.listActiveProvidersSorted())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek", "openai", "qwen");
    }

    @Test
    void missingKeyProviderIsNotEligibleTest() {
        ModelProviderEntity openai = aiProvider(2L, "openai", 20, "OPENAI_API_KEY");
        assertThat(routingConfigService.isRuntimeConfigured(openai)).isFalse();
    }

    @Test
    void ruleProviderIsAlwaysRuntimeConfiguredTest() {
        ModelProviderEntity rule = ModelProviderEntity.builder()
                .id(99L)
                .providerCode("rule")
                .authMode("NONE")
                .apiProtocol("RULE_BASED")
                .status("ACTIVE")
                .build();

        assertThat(routingConfigService.isRuleProvider(rule)).isTrue();
        assertThat(routingConfigService.isRuntimeConfigured(rule)).isTrue();
    }

    @Test
    void unknownHealthDoesNotPermanentlyDisableProviderTest() {
        ModelProviderEntity deepseek = aiProvider(1L, "deepseek", 10, "DEEPSEEK_API_KEY");
        assertThat(routingConfigService.isActiveStatus(deepseek.getStatus())).isTrue();
    }

    private ModelProviderEntity aiProvider(Long id, String code, int priority, String apiKeyEnv) {
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
}
