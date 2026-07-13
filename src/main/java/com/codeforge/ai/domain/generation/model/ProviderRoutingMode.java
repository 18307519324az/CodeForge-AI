package com.codeforge.ai.domain.generation.model;

/**
 * Runtime AI provider routing mode derived from {@code codeforge.ai.provider}.
 *
 * <ul>
 *   <li>{@link #AUTO} — use all ACTIVE, runtime-configured AI providers ordered by priority.</li>
 *   <li>{@link #PIN} — use only the configured provider code when available.</li>
 * </ul>
 */
public enum ProviderRoutingMode {
    AUTO,
    PIN;

    public static ProviderRoutingMode fromConfigValue(String providerConfig) {
        if (providerConfig == null) {
            return AUTO;
        }
        String trimmed = providerConfig.trim();
        if (trimmed.isEmpty() || "auto".equalsIgnoreCase(trimmed) || "rule".equalsIgnoreCase(trimmed)) {
            return AUTO;
        }
        return PIN;
    }

    public static String pinnedProviderCode(String providerConfig) {
        if (fromConfigValue(providerConfig) != PIN) {
            return null;
        }
        return providerConfig.trim();
    }
}
