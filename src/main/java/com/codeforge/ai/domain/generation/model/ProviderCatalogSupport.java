package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import java.util.Set;

public final class ProviderCatalogSupport {

    public static final Set<String> PSEUDO_PROVIDER_CODES = Set.of("auto");
    public static final Set<String> RESERVED_ROUTING_CODES = Set.of("auto", "rule");

    private ProviderCatalogSupport() {
    }

    public static boolean isPseudoProvider(ModelProviderEntity provider) {
        return provider != null
                && provider.getProviderCode() != null
                && PSEUDO_PROVIDER_CODES.contains(provider.getProviderCode().trim().toLowerCase());
    }

    public static boolean isReservedProviderCode(String providerCode) {
        return providerCode != null
                && RESERVED_ROUTING_CODES.contains(providerCode.trim().toLowerCase());
    }

    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
