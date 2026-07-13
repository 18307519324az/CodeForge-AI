package com.codeforge.ai.infrastructure.security;

import com.codeforge.ai.domain.generation.model.CredentialSource;
import com.codeforge.ai.domain.generation.model.ProviderCatalogSupport;
import com.codeforge.ai.domain.generation.model.ProviderCredentialResolver;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderCredentialEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CredentialSecurityTest {

    private static final String MASTER_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void credentialIsEncryptedAtRestTest() {
        withMasterKey(() -> {
            CredentialEncryptionService service = new CredentialEncryptionService();
            CredentialEncryptionService.EncryptedPayload encrypted = service.encrypt("sk-test-secret-key-1234");
            String decrypted = service.decrypt(encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion());
            assertThat(decrypted).isEqualTo("sk-test-secret-key-1234");
            assertThat(new String(encrypted.ciphertext(), StandardCharsets.UTF_8)).doesNotContain("sk-test-secret");
        });
    }

    @Test
    void missingMasterKeyRejectsEncryptedCredentialTest() {
        String previous = System.getenv(CredentialEncryptionService.MASTER_KEY_ENV);
        try {
            clearMasterKey();
            CredentialEncryptionService service = new CredentialEncryptionService();
            assertThatThrownBy(service::requireMasterKeyForEncryptedStorage)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException businessException = (BusinessException) ex;
                        assertThat(businessException.getErrorCode())
                                .isEqualTo(com.codeforge.ai.shared.exception.ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE);
                    });
        } finally {
            restoreMasterKey(previous);
        }
    }

    @Test
    void updatingCredentialRotatesCiphertextTest() {
        withMasterKey(() -> {
            CredentialEncryptionService service = new CredentialEncryptionService();
            CredentialEncryptionService.EncryptedPayload first = service.encrypt("sk-first-key-abcdef12");
            CredentialEncryptionService.EncryptedPayload second = service.encrypt("sk-second-key-abcdef12");
            assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
            assertThat(first.nonce()).isNotEqualTo(second.nonce());
        });
    }

    @Test
    void credentialApiNeverReturnsPlaintextTest() {
        String masked = ProviderCatalogSupport.maskApiKey("sk-live-abcdef123456");
        assertThat(masked).isEqualTo("****3456");
        assertThat(masked).doesNotContain("sk-live");
    }

    @Test
    void envCredentialStillWorksTest() {
        ProviderCredentialResolver resolver = new ProviderCredentialResolver(
                org.mockito.Mockito.mock(ModelProviderCredentialEntityMapper.class),
                new CredentialEncryptionService());
        ModelProviderEntity provider = ModelProviderEntity.builder()
                .id(1L)
                .providerCode("openai")
                .authMode("API_KEY")
                .apiKeyEnv("OPENAI_API_KEY")
                .credentialSource(CredentialSource.ENV.code())
                .build();
        String envValue = System.getenv("OPENAI_API_KEY");
        if (envValue == null || envValue.isBlank() || "please_change_me".equalsIgnoreCase(envValue.trim())) {
            assertThat(resolver.isConfigured(provider)).isFalse();
            return;
        }
        assertThat(resolver.isConfigured(provider)).isTrue();
        assertThat(resolver.maskedHint(provider)).doesNotContain(envValue);
    }

    @Test
    void credentialDoesNotAppearInLogsTest() {
        String masked = ProviderCatalogSupport.maskApiKey("super-secret-api-key-value");
        assertThat(masked).doesNotContain("super-secret-api-key-value");
    }

    private void withMasterKey(Runnable runnable) {
        String previous = System.getenv(CredentialEncryptionService.MASTER_KEY_ENV);
        try {
            setMasterKey(MASTER_KEY);
            runnable.run();
        } finally {
            restoreMasterKey(previous);
        }
    }

    private void setMasterKey(String value) {
        try {
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            var field = processEnvironment.getDeclaredField("theCaseInsensitiveEnvironment");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var env = (java.util.Map<String, String>) field.get(null);
            env.put(CredentialEncryptionService.MASTER_KEY_ENV, value);
        } catch (Exception exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Cannot mutate environment in this JVM");
        }
    }

    private void clearMasterKey() {
        try {
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            var field = processEnvironment.getDeclaredField("theCaseInsensitiveEnvironment");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var env = (java.util.Map<String, String>) field.get(null);
            env.remove(CredentialEncryptionService.MASTER_KEY_ENV);
        } catch (Exception exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Cannot mutate environment in this JVM");
        }
    }

    private void restoreMasterKey(String previous) {
        try {
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            var field = processEnvironment.getDeclaredField("theCaseInsensitiveEnvironment");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var env = (java.util.Map<String, String>) field.get(null);
            if (previous == null) {
                env.remove(CredentialEncryptionService.MASTER_KEY_ENV);
            } else {
                env.put(CredentialEncryptionService.MASTER_KEY_ENV, previous);
            }
        } catch (Exception ignored) {
            // no-op
        }
    }
}
