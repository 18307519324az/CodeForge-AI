package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelProviderRoutingConfigService {

    private static final Logger log = LoggerFactory.getLogger(ModelProviderRoutingConfigService.class);
    private static final long CACHE_TTL_MS = 5_000L;

    private final ModelProviderEntityMapper providerMapper;
    private final ProviderCredentialResolver credentialResolver;

    private volatile List<ModelProviderEntity> cachedProviders = List.of();
    private volatile long cacheLoadedAtMs = 0L;

    public List<ModelProviderEntity> listActiveProvidersSorted() {
        refreshCacheIfStale();
        return cachedProviders;
    }

    public void invalidateCache() {
        cacheLoadedAtMs = 0L;
    }

    public boolean isRuntimeConfigured(ModelProviderEntity provider) {
        if (provider == null) {
            return false;
        }
        if (isRuleProvider(provider)) {
            return true;
        }
        if (ProviderCatalogSupport.isPseudoProvider(provider)) {
            return false;
        }
        if ("NONE".equalsIgnoreCase(provider.getAuthMode())) {
            return false;
        }
        boolean configured = credentialResolver.isConfigured(provider);
        if (!configured) {
            log.debug("Provider {} skipped: credential not configured", provider.getProviderCode());
        }
        return configured;
    }

    public boolean isActiveStatus(String status) {
        return status != null && "ACTIVE".equalsIgnoreCase(status.trim());
    }

    public boolean isRuleProvider(ModelProviderEntity provider) {
        return provider != null && ("RULE_BASED".equalsIgnoreCase(provider.getApiProtocol())
                || "rule".equalsIgnoreCase(provider.getProviderCode()));
    }

    private void refreshCacheIfStale() {
        long now = System.currentTimeMillis();
        if (!cachedProviders.isEmpty() && now - cacheLoadedAtMs < CACHE_TTL_MS) {
            return;
        }
        cachedProviders = providerMapper.findAll().stream()
                .filter(provider -> provider.getIsDeleted() == null || provider.getIsDeleted() == 0)
                .filter(provider -> isActiveStatus(provider.getStatus()))
                .filter(provider -> !ProviderCatalogSupport.isPseudoProvider(provider))
                .sorted(Comparator
                        .comparing((ModelProviderEntity provider) ->
                                provider.getPriority() == null ? Integer.MAX_VALUE : provider.getPriority())
                        .thenComparing(ModelProviderEntity::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toUnmodifiableList());
        cacheLoadedAtMs = now;
    }
}
