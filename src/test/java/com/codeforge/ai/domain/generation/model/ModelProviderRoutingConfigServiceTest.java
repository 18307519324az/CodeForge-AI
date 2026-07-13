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
class ModelProviderRoutingConfigServiceTest {

    @Mock
    private ModelProviderEntityMapper providerMapper;

    @Mock
    private ProviderCredentialResolver credentialResolver;

    private ModelProviderRoutingConfigService routingConfigService;

    @BeforeEach
    void setUp() {
        routingConfigService = new ModelProviderRoutingConfigService(providerMapper, credentialResolver);
    }

    @Test
    void shouldOnlyIncludeActiveProvidersSortedByPriority() {
        given(providerMapper.findAll()).willReturn(List.of(
                activeProvider(2L, "openai", 20),
                disabledProvider(3L, "anthropic", 10),
                activeProvider(1L, "deepseek", 5)
        ));

        List<ModelProviderEntity> providers = routingConfigService.listActiveProvidersSorted();

        assertThat(providers).extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek", "openai");
    }

    @Test
    void shouldExcludePseudoAutoProvider() {
        ModelProviderEntity auto = activeProvider(9L, "auto", 10);
        given(providerMapper.findAll()).willReturn(List.of(auto, activeProvider(1L, "deepseek", 5)));

        assertThat(routingConfigService.listActiveProvidersSorted())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("deepseek");
    }

    @Test
    void shouldInvalidateCacheAfterProviderDisabled() {
        given(providerMapper.findAll()).willReturn(List.of(activeProvider(1L, "deepseek", 1)));
        assertThat(routingConfigService.listActiveProvidersSorted()).hasSize(1);

        given(providerMapper.findAll()).willReturn(List.of(disabledProvider(1L, "deepseek", 1)));
        assertThat(routingConfigService.listActiveProvidersSorted()).hasSize(1);

        routingConfigService.invalidateCache();
        assertThat(routingConfigService.listActiveProvidersSorted()).isEmpty();
    }

    @Test
    void shouldTreatInactiveAsDisabled() {
        assertThat(routingConfigService.isActiveStatus("ACTIVE")).isTrue();
        assertThat(routingConfigService.isActiveStatus("DISABLED")).isFalse();
    }

    private ModelProviderEntity activeProvider(Long id, String code, int priority) {
        return ModelProviderEntity.builder()
                .id(id)
                .providerCode(code)
                .providerName(code)
                .baseUrl("https://api.example.com")
                .authMode("API_KEY")
                .apiKeyEnv("OPENAI_API_KEY")
                .apiProtocol("OPENAI_COMPATIBLE")
                .defaultModel("gpt-test")
                .priority(priority)
                .status("ACTIVE")
                .isDeleted(0)
                .build();
    }

    private ModelProviderEntity disabledProvider(Long id, String code, int priority) {
        ModelProviderEntity provider = activeProvider(id, code, priority);
        provider.setStatus("DISABLED");
        return provider;
    }
}
