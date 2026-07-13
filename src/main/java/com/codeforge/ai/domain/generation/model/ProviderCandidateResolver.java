package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderCandidateResolver {

    private final ModelProviderRoutingConfigService routingConfigService;
    private final AiRoutingConfigService aiRoutingConfigService;

    public ProviderRoutingMode routingMode() {
        return aiRoutingConfigService.getEffectiveConfig().mode();
    }

    public List<ModelProviderEntity> resolveAvailableProviders() {
        List<ModelProviderEntity> available = new ArrayList<>(resolveAiProviders());
        ModelProviderEntity ruleProvider = resolveRuleProvider();
        if (ruleProvider != null) {
            available.add(ruleProvider);
        }
        return available;
    }

    public List<ModelProviderEntity> resolveAiProviders() {
        AiRoutingConfigService.EffectiveRoutingConfig routing = aiRoutingConfigService.getEffectiveConfig();
        List<ModelProviderEntity> configured = routingConfigService.listActiveProvidersSorted().stream()
                .filter(provider -> !isRuleProvider(provider))
                .filter(routingConfigService::isRuntimeConfigured)
                .toList();
        if (routing.mode() == ProviderRoutingMode.AUTO) {
            return configured;
        }
        String pinnedCode = routing.pinnedProviderCode();
        if (pinnedCode == null || pinnedCode.isBlank()) {
            return List.of();
        }
        return configured.stream()
                .filter(provider -> pinnedCode.equalsIgnoreCase(provider.getProviderCode()))
                .toList();
    }

    public ModelProviderEntity resolveRuleProvider() {
        return routingConfigService.listActiveProvidersSorted().stream()
                .filter(this::isRuleProvider)
                .findFirst()
                .orElse(null);
    }

    public boolean hasConfiguredAiProvider() {
        return !resolveAiProviders().isEmpty();
    }

    private boolean isRuleProvider(ModelProviderEntity provider) {
        return routingConfigService.isRuleProvider(provider);
    }
}
