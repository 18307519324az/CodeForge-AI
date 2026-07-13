package com.codeforge.ai.domain.generation.model;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderConfigCacheInvalidator {

    private final ModelProviderRoutingConfigService routingConfigService;

    public void invalidateAfterProviderChange() {
        routingConfigService.invalidateCache();
    }
}
