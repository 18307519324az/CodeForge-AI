package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.ModelProviderCreateRequest;
import com.codeforge.ai.application.dto.admin.ModelProviderStatusUpdateRequest;
import com.codeforge.ai.application.dto.admin.ModelProviderUpdateRequest;
import com.codeforge.ai.application.dto.admin.ProviderCredentialUpsertRequest;
import com.codeforge.ai.application.dto.admin.ProviderHealthCheckResponse;
import com.codeforge.ai.domain.generation.model.ModelProviderRoutingConfigService;
import com.codeforge.ai.domain.generation.model.ProviderConfigCacheInvalidator;
import com.codeforge.ai.domain.generation.model.ProviderCredentialResolver;
import com.codeforge.ai.domain.generation.model.ProviderHealthCheckService;
import com.codeforge.ai.domain.model.entity.ModelProviderCredentialEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderCredentialEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import com.codeforge.ai.infrastructure.security.CredentialEncryptionService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class AdminModelProviderApplicationServiceTest {

    private ModelProviderEntityMapper modelProviderEntityMapper;
    private ModelProviderCredentialEntityMapper credentialEntityMapper;
    private AuditLogWriter auditLogWriter;
    private ProviderConfigCacheInvalidator cacheInvalidator;
    private ModelProviderRoutingConfigService routingConfigService;
    private ProviderCredentialResolver credentialResolver;
    private CredentialEncryptionService encryptionService;
    private ProviderHealthCheckService healthCheckService;
    private AdminModelProviderApplicationService adminModelProviderApplicationService;

    @BeforeEach
    void setUp() {
        modelProviderEntityMapper = mock(ModelProviderEntityMapper.class);
        credentialEntityMapper = mock(ModelProviderCredentialEntityMapper.class);
        auditLogWriter = mock(AuditLogWriter.class);
        routingConfigService = mock(ModelProviderRoutingConfigService.class);
        credentialResolver = mock(ProviderCredentialResolver.class);
        encryptionService = mock(CredentialEncryptionService.class);
        cacheInvalidator = mock(ProviderConfigCacheInvalidator.class);
        healthCheckService = new ProviderHealthCheckService(routingConfigService);
        adminModelProviderApplicationService = new AdminModelProviderApplicationService(
                modelProviderEntityMapper,
                credentialEntityMapper,
                auditLogWriter,
                new ObjectMapper(),
                cacheInvalidator,
                routingConfigService,
                credentialResolver,
                encryptionService,
                healthCheckService
        );
        given(credentialResolver.isConfigured(any(ModelProviderEntity.class))).willReturn(false);
        given(credentialResolver.maskedHint(any(ModelProviderEntity.class))).willReturn(null);
    }

    @Test
    void shouldCreateModelProvider() {
        ModelProviderCreateRequest request = new ModelProviderCreateRequest();
        request.setProviderCode("deepseek");
        request.setProviderName("DeepSeek");
        request.setBaseUrl("https://api.deepseek.com");
        request.setAuthMode("API_KEY");
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(null);
        doAnswer(invocation -> {
            ModelProviderEntity entity = invocation.getArgument(0);
            entity.setId(3001L);
            return 1;
        }).when(modelProviderEntityMapper).insertProvider(any(ModelProviderEntity.class));
        given(modelProviderEntityMapper.findById(3001L)).willReturn(ModelProviderEntity.builder()
                .id(3001L)
                .providerCode("deepseek")
                .providerName("DeepSeek")
                .baseUrl("https://api.deepseek.com")
                .authMode("API_KEY")
                .credentialSource("ENV")
                .status("ACTIVE")
                .build());

        var response = adminModelProviderApplicationService.createModelProvider(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                request
        );

        assertThat(response.id()).isEqualTo(3001L);
        assertThat(response.providerCode()).isEqualTo("deepseek");
        verify(auditLogWriter).insert(any());
    }

    @Test
    void shouldRejectDuplicateProviderCodeOnCreate() {
        ModelProviderCreateRequest request = new ModelProviderCreateRequest();
        request.setProviderCode("deepseek");
        request.setProviderName("DeepSeek");
        request.setAuthMode("API_KEY");
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(
                ModelProviderEntity.builder().id(3001L).providerCode("deepseek").build()
        );

        assertThatThrownBy(() -> adminModelProviderApplicationService.createModelProvider(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                request
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldUpdateModelProvider() {
        ModelProviderUpdateRequest request = new ModelProviderUpdateRequest();
        request.setProviderName("DeepSeek V2");
        request.setBaseUrl("https://api.deepseek.com/v2");
        request.setAuthMode("API_KEY");
        ModelProviderEntity existingEntity = ModelProviderEntity.builder()
                .id(3001L)
                .providerCode("deepseek")
                .providerName("DeepSeek")
                .baseUrl("https://api.deepseek.com")
                .authMode("API_KEY")
                .status("ACTIVE")
                .build();
        ModelProviderEntity refreshedEntity = ModelProviderEntity.builder()
                .id(3001L)
                .providerCode("deepseek")
                .providerName("DeepSeek V2")
                .baseUrl("https://api.deepseek.com/v2")
                .authMode("API_KEY")
                .status("ACTIVE")
                .build();
        given(modelProviderEntityMapper.findById(3001L)).willReturn(existingEntity, refreshedEntity);
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(existingEntity);

        var response = adminModelProviderApplicationService.updateModelProvider(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                3001L,
                request
        );

        assertThat(response.providerName()).isEqualTo("DeepSeek V2");
        ArgumentCaptor<ModelProviderEntity> captor = ArgumentCaptor.forClass(ModelProviderEntity.class);
        verify(modelProviderEntityMapper).updateProviderAdmin(captor.capture());
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://api.deepseek.com/v2");
        verify(cacheInvalidator).invalidateAfterProviderChange();
    }

    @Test
    void shouldDisableProviderAndInvalidateCache() {
        ModelProviderStatusUpdateRequest request = new ModelProviderStatusUpdateRequest();
        request.setStatus("DISABLED");
        ModelProviderEntity existingEntity = ModelProviderEntity.builder()
                .id(3001L)
                .providerCode("deepseek")
                .status("ACTIVE")
                .build();
        ModelProviderEntity refreshedEntity = ModelProviderEntity.builder()
                .id(3001L)
                .providerCode("deepseek")
                .status("DISABLED")
                .build();
        given(modelProviderEntityMapper.findById(3001L)).willReturn(existingEntity, refreshedEntity);

        var response = adminModelProviderApplicationService.updateModelProviderStatus(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                3001L,
                request
        );

        assertThat(response.status()).isEqualTo("DISABLED");
        verify(modelProviderEntityMapper).updateStatus(3001L, "DISABLED", 1L);
        verify(cacheInvalidator).invalidateAfterProviderChange();
    }

    @Test
    void shouldHealthCheckProviderUsingFindByIdNotFlexSelectOneById() {
        ModelProviderEntity deepseek = ModelProviderEntity.builder()
                .id(3001L)
                .providerCode("deepseek")
                .providerName("DeepSeek")
                .baseUrl("https://api.deepseek.com")
                .authMode("API_KEY")
                .apiKeyEnv("DEEPSEEK_API_KEY")
                .status("ACTIVE")
                .build();
        given(modelProviderEntityMapper.findById(3001L)).willReturn(deepseek);
        given(routingConfigService.isActiveStatus("ACTIVE")).willReturn(true);
        given(routingConfigService.isRuleProvider(deepseek)).willReturn(false);
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);

        ProviderHealthCheckResponse response = adminModelProviderApplicationService.healthCheckModelProvider(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                3001L
        );

        assertThat(response.healthy()).isTrue();
        verify(modelProviderEntityMapper).findById(3001L);
    }

    @Test
    void shouldReuseSoftDeletedCredentialOnRecreate() {
        ModelProviderEntity openai = ModelProviderEntity.builder()
                .id(3001L)
                .providerCode("openai")
                .providerName("OpenAI")
                .baseUrl("https://api.openai.com/v1")
                .authMode("API_KEY")
                .credentialSource("ENCRYPTED_DB")
                .status("ACTIVE")
                .build();
        ModelProviderCredentialEntity deletedCredential = ModelProviderCredentialEntity.builder()
                .id(9001L)
                .providerId(3001L)
                .credentialType("API_KEY")
                .ciphertext(new byte[] {1})
                .nonce(new byte[] {2})
                .keyVersion(1)
                .maskedHint("****old")
                .isDeleted(1)
                .build();
        given(modelProviderEntityMapper.findById(3001L)).willReturn(openai);
        given(routingConfigService.isRuleProvider(openai)).willReturn(false);
        given(credentialEntityMapper.findActiveByProviderId(3001L)).willReturn(null);
        given(credentialEntityMapper.findByProviderId(3001L)).willReturn(deletedCredential);
        given(encryptionService.encrypt("sk-synth-recreate")).willReturn(
                new CredentialEncryptionService.EncryptedPayload(new byte[] {3}, new byte[] {4}, 1)
        );

        ProviderCredentialUpsertRequest request = new ProviderCredentialUpsertRequest();
        request.setApiKey("sk-synth-recreate");

        adminModelProviderApplicationService.upsertProviderCredential(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                3001L,
                request
        );

        verify(credentialEntityMapper, never()).insertCredential(any());
        ArgumentCaptor<ModelProviderCredentialEntity> captor =
                ArgumentCaptor.forClass(ModelProviderCredentialEntity.class);
        verify(credentialEntityMapper).upsertByProviderId(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9001L);
        assertThat(captor.getValue().getIsDeleted()).isEqualTo(0);
        assertThat(captor.getValue().getCiphertext()).containsExactly(3);
        verify(cacheInvalidator).invalidateAfterProviderChange();
    }

    @Test
    void shouldRejectMissingProviderOnUpdate() {
        ModelProviderUpdateRequest request = new ModelProviderUpdateRequest();
        request.setProviderName("DeepSeek");
        request.setAuthMode("API_KEY");
        given(modelProviderEntityMapper.findById(3001L)).willReturn(null);

        assertThatThrownBy(() -> adminModelProviderApplicationService.updateModelProvider(
                new CurrentUser(1L, "admin", java.util.List.of("PLATFORM_ADMIN")),
                3001L,
                request
        )).isInstanceOf(BusinessException.class);
    }
}
