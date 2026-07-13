package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.ModelProviderStatusUpdateRequest;
import com.codeforge.ai.application.dto.admin.ModelProviderUpdateRequest;
import com.codeforge.ai.application.dto.admin.ProviderCredentialUpsertRequest;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.generation.model.ModelProviderRoutingConfigService;
import com.codeforge.ai.domain.generation.model.ProviderConfigCacheInvalidator;
import com.codeforge.ai.domain.generation.model.ProviderCredentialResolver;
import com.codeforge.ai.domain.generation.model.ProviderHealthCheckService;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderCredentialEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import com.codeforge.ai.infrastructure.security.CredentialEncryptionService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminModelProviderAuditContractTest {

    private static final String MASTER_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private ModelProviderEntityMapper modelProviderEntityMapper;
    private ModelProviderCredentialEntityMapper credentialEntityMapper;
    private AuditLogWriter auditLogWriter;
    private ProviderConfigCacheInvalidator cacheInvalidator;
    private ModelProviderRoutingConfigService routingConfigService;
    private ProviderCredentialResolver credentialResolver;
    private CredentialEncryptionService encryptionService;
    private AdminModelProviderApplicationService service;
    private CurrentUser admin;

    @BeforeEach
    void setUp() {
        modelProviderEntityMapper = mock(ModelProviderEntityMapper.class);
        credentialEntityMapper = mock(ModelProviderCredentialEntityMapper.class);
        auditLogWriter = mock(AuditLogWriter.class);
        routingConfigService = mock(ModelProviderRoutingConfigService.class);
        credentialResolver = mock(ProviderCredentialResolver.class);
        encryptionService = mock(CredentialEncryptionService.class);
        cacheInvalidator = mock(ProviderConfigCacheInvalidator.class);
        service = new AdminModelProviderApplicationService(
                modelProviderEntityMapper,
                credentialEntityMapper,
                auditLogWriter,
                new ObjectMapper(),
                cacheInvalidator,
                routingConfigService,
                credentialResolver,
                encryptionService,
                new ProviderHealthCheckService(routingConfigService)
        );
        admin = new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN"));
        given(credentialResolver.isConfigured(any(ModelProviderEntity.class))).willReturn(false);
        given(credentialResolver.maskedHint(any(ModelProviderEntity.class))).willReturn(null);
    }

    @Test
    void providerPriorityUpdateWritesAuditCreatedAtTest() {
        ModelProviderUpdateRequest request = new ModelProviderUpdateRequest();
        request.setProviderName("OpenAI");
        request.setAuthMode("API_KEY");
        request.setPriority(5);
        ModelProviderEntity existing = openaiEntity();
        ModelProviderEntity refreshed = openaiEntity();
        refreshed.setPriority(5);
        given(modelProviderEntityMapper.findById(20L)).willReturn(existing, refreshed);

        service.updateModelProvider(admin, 20L, request);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogWriter).insert(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo("MODEL_PROVIDER_UPDATE");
    }

    @Test
    void providerStatusUpdateWritesAuditCreatedAtTest() {
        ModelProviderStatusUpdateRequest request = new ModelProviderStatusUpdateRequest();
        request.setStatus("DISABLED");
        ModelProviderEntity existing = openaiEntity();
        ModelProviderEntity refreshed = openaiEntity();
        refreshed.setStatus("DISABLED");
        given(modelProviderEntityMapper.findById(20L)).willReturn(existing, refreshed);

        service.updateModelProviderStatus(admin, 20L, request);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogWriter).insert(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo("MODEL_PROVIDER_STATUS_UPDATE");
    }

    @Test
    void providerCredentialMutationWritesAuditCreatedAtTest() {
        withMasterKey(() -> {
            CredentialEncryptionService realEncryption = new CredentialEncryptionService();
            AdminModelProviderApplicationService realService = buildService(realEncryption);
            ModelProviderEntity openai = openaiEntity();
            openai.setCredentialSource("ENCRYPTED_DB");
            given(modelProviderEntityMapper.findById(20L)).willReturn(openai);
            given(routingConfigService.isRuleProvider(openai)).willReturn(false);
            given(credentialEntityMapper.findActiveByProviderId(20L)).willReturn(null);
            doAnswer(invocation -> 1).when(credentialEntityMapper).insertCredential(any());

            ProviderCredentialUpsertRequest request = new ProviderCredentialUpsertRequest();
            request.setApiKey("sk-synth-audit-contract-key");

            realService.upsertProviderCredential(admin, 20L, request);

            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(auditLogWriter).insert(captor.capture());
            assertThat(captor.getValue().getActionCode()).isEqualTo("MODEL_PROVIDER_CREDENTIAL_UPSERT");
        });
    }

    @Test
    void missingMasterKeyReturnsSafeServiceUnavailableTest() {
        CredentialEncryptionService encryptionService = mock(CredentialEncryptionService.class);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE))
                .when(encryptionService).requireMasterKeyForEncryptedStorage();
        AdminModelProviderApplicationService realService = buildService(encryptionService);
        ModelProviderEntity openai = openaiEntity();
        given(modelProviderEntityMapper.findById(20L)).willReturn(openai);
        given(routingConfigService.isRuleProvider(openai)).willReturn(false);

        ProviderCredentialUpsertRequest request = new ProviderCredentialUpsertRequest();
        request.setApiKey("sk-synth-missing-master-key");

        assertThatThrownBy(() -> realService.upsertProviderCredential(admin, 20L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE);
                    assertThat(businessException.getCode()).isEqualTo(50302);
                });
    }

    @Test
    void missingMasterKeyDoesNotPersistCredentialTest() {
        CredentialEncryptionService encryptionService = mock(CredentialEncryptionService.class);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE))
                .when(encryptionService).requireMasterKeyForEncryptedStorage();
        AdminModelProviderApplicationService realService = buildService(encryptionService);
        ModelProviderEntity openai = openaiEntity();
        given(modelProviderEntityMapper.findById(20L)).willReturn(openai);
        given(routingConfigService.isRuleProvider(openai)).willReturn(false);

        ProviderCredentialUpsertRequest request = new ProviderCredentialUpsertRequest();
        request.setApiKey("sk-synth-missing-master-key");

        assertThatThrownBy(() -> realService.upsertProviderCredential(admin, 20L, request))
                .isInstanceOf(BusinessException.class);

        verify(credentialEntityMapper, never()).insertCredential(any());
        verify(credentialEntityMapper, never()).upsertByProviderId(any());
        verify(modelProviderEntityMapper, never()).updateProviderAdmin(any());
    }

    @Test
    void missingMasterKeyNeverFallsBackToPlaintextTest() {
        CredentialEncryptionService encryptionService = mock(CredentialEncryptionService.class);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE))
                .when(encryptionService).encrypt(org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> encryptionService.encrypt("sk-synth-plaintext-fallback"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE));
    }

    private AdminModelProviderApplicationService buildService(CredentialEncryptionService encryption) {
        return new AdminModelProviderApplicationService(
                modelProviderEntityMapper,
                credentialEntityMapper,
                auditLogWriter,
                new ObjectMapper(),
                cacheInvalidator,
                routingConfigService,
                credentialResolver,
                encryption,
                new ProviderHealthCheckService(routingConfigService)
        );
    }

    private ModelProviderEntity openaiEntity() {
        return ModelProviderEntity.builder()
                .id(20L)
                .providerCode("openai")
                .providerName("OpenAI")
                .baseUrl("https://api.openai.com/v1")
                .authMode("API_KEY")
                .credentialSource("ENV")
                .status("ACTIVE")
                .priority(20)
                .build();
    }

    private void withMasterKey(Runnable action) {
        String previous = System.getenv(CredentialEncryptionService.MASTER_KEY_ENV);
        try {
            setMasterKey(MASTER_KEY);
            action.run();
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
            if (value == null || value.isBlank()) {
                env.remove(CredentialEncryptionService.MASTER_KEY_ENV);
            } else {
                env.put(CredentialEncryptionService.MASTER_KEY_ENV, value);
            }
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
