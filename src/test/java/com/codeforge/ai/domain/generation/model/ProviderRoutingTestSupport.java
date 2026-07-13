package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import org.mockito.Mockito;

import static org.mockito.BDDMockito.given;

final class ProviderRoutingTestSupport {

    private ProviderRoutingTestSupport() {
    }

    static void stubPinnedRouting(AiRoutingConfigService aiRoutingConfigService, String providerConfig) {
        ProviderRoutingMode mode = ProviderRoutingMode.fromConfigValue(providerConfig);
        String pinned = ProviderRoutingMode.pinnedProviderCode(providerConfig);
        given(aiRoutingConfigService.getEffectiveConfig())
                .willReturn(new AiRoutingConfigService.EffectiveRoutingConfig(mode, pinned, false));
    }

    static void stubAutoRouting(AiRoutingConfigService aiRoutingConfigService) {
        stubPinnedRouting(aiRoutingConfigService, "auto");
    }

    static ProviderCredentialResolver lenientConfiguredResolver() {
        ProviderCredentialResolver resolver = Mockito.mock(ProviderCredentialResolver.class);
        given(resolver.isConfigured(Mockito.any(ModelProviderEntity.class))).willAnswer(invocation -> {
            ModelProviderEntity provider = invocation.getArgument(0);
            if (provider == null) {
                return false;
            }
            if ("rule".equalsIgnoreCase(provider.getProviderCode())
                    || "RULE_BASED".equalsIgnoreCase(provider.getApiProtocol())) {
                return true;
            }
            return provider.getApiKeyEnv() != null && "PATH".equals(provider.getApiKeyEnv());
        });
        return resolver;
    }
}
