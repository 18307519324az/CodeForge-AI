package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderCredentialEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderCredentialEntityMapper;
import com.codeforge.ai.infrastructure.security.CredentialEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderCredentialResolver {

    private final ModelProviderCredentialEntityMapper credentialMapper;
    private final CredentialEncryptionService encryptionService;

    public CredentialSource resolveCredentialSource(ModelProviderEntity provider) {
        if (provider == null) {
            return CredentialSource.NONE;
        }
        if (routingConfigServiceIsRule(provider)) {
            return CredentialSource.NONE;
        }
        return CredentialSource.fromValue(provider.getCredentialSource());
    }

    public boolean isConfigured(ModelProviderEntity provider) {
        if (provider == null) {
            return false;
        }
        if (routingConfigServiceIsRule(provider)) {
            return true;
        }
        return switch (resolveCredentialSource(provider)) {
            case NONE -> false;
            case ENV -> hasValidEnvKey(provider);
            case ENCRYPTED_DB -> credentialMapper.findActiveByProviderId(provider.getId()) != null;
        };
    }

    public String resolveApiKey(ModelProviderEntity provider) {
        if (provider == null || routingConfigServiceIsRule(provider)) {
            return null;
        }
        return switch (resolveCredentialSource(provider)) {
            case NONE -> null;
            case ENV -> readEnvKey(provider);
            case ENCRYPTED_DB -> readEncryptedKey(provider);
        };
    }

    public String maskedHint(ModelProviderEntity provider) {
        if (provider == null || routingConfigServiceIsRule(provider)) {
            return null;
        }
        return switch (resolveCredentialSource(provider)) {
            case NONE -> null;
            case ENV -> {
                String key = readEnvKey(provider);
                yield ProviderCatalogSupport.maskApiKey(key);
            }
            case ENCRYPTED_DB -> {
                ModelProviderCredentialEntity credential = credentialMapper.findActiveByProviderId(provider.getId());
                yield credential != null ? credential.getMaskedHint() : null;
            }
        };
    }

    private String readEncryptedKey(ModelProviderEntity provider) {
        ModelProviderCredentialEntity credential = credentialMapper.findActiveByProviderId(provider.getId());
        if (credential == null) {
            return null;
        }
        return encryptionService.decrypt(credential.getCiphertext(), credential.getNonce(), credential.getKeyVersion());
    }

    private boolean hasValidEnvKey(ModelProviderEntity provider) {
        String key = readEnvKey(provider);
        return key != null && !key.isBlank() && !"please_change_me".equalsIgnoreCase(key.trim());
    }

    private String readEnvKey(ModelProviderEntity provider) {
        if (provider.getApiKeyEnv() == null || provider.getApiKeyEnv().isBlank()) {
            return null;
        }
        return System.getenv(provider.getApiKeyEnv());
    }

    private boolean routingConfigServiceIsRule(ModelProviderEntity provider) {
        return "RULE_BASED".equalsIgnoreCase(provider.getApiProtocol())
                || "rule".equalsIgnoreCase(provider.getProviderCode());
    }
}
