package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.ModelProviderCreateRequest;
import com.codeforge.ai.application.dto.admin.ModelProviderResponse;
import com.codeforge.ai.application.dto.admin.ModelProviderStatusUpdateRequest;
import com.codeforge.ai.application.dto.admin.ModelProviderUpdateRequest;
import com.codeforge.ai.application.dto.admin.ProviderCredentialResponse;
import com.codeforge.ai.application.dto.admin.ProviderCredentialUpsertRequest;
import com.codeforge.ai.application.dto.admin.ProviderHealthCheckResponse;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.generation.model.CredentialSource;
import com.codeforge.ai.domain.generation.model.ModelProviderRoutingConfigService;
import com.codeforge.ai.domain.generation.model.ProviderCatalogSupport;
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
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.ResultUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminModelProviderApplicationService {

    private static final String MODEL_PROVIDER_NOT_FOUND_MESSAGE = "模型提供方不存在";

    private final ModelProviderEntityMapper modelProviderEntityMapper;
    private final ModelProviderCredentialEntityMapper credentialEntityMapper;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;
    private final ProviderConfigCacheInvalidator cacheInvalidator;
    private final ModelProviderRoutingConfigService routingConfigService;
    private final ProviderCredentialResolver credentialResolver;
    private final CredentialEncryptionService encryptionService;
    private final ProviderHealthCheckService healthCheckService;

    @Transactional
    public ModelProviderResponse createModelProvider(CurrentUser currentUser, ModelProviderCreateRequest request) {
        requirePlatformAdmin(currentUser);
        rejectReservedProviderCode(request.getProviderCode());
        ensureProviderCodeUnique(null, request.getProviderCode());
        ModelProviderEntity entity = ModelProviderEntity.builder()
                .providerCode(request.getProviderCode())
                .providerName(request.getProviderName())
                .baseUrl(request.getBaseUrl())
                .authMode(request.getAuthMode())
                .credentialSource(CredentialSource.ENV.code())
                .status("ACTIVE")
                .build();
        entity.setCreatedBy(currentUser.requiredUserId());
        entity.setUpdatedBy(currentUser.requiredUserId());
        modelProviderEntityMapper.insertProvider(entity);
        auditLogWriter.insert(buildAuditLog(currentUser.requiredUserId(), "MODEL_PROVIDER_CREATE", entity));
        cacheInvalidator.invalidateAfterProviderChange();
        return toResponse(requireProvider(entity.getId()));
    }

    @Transactional
    public ModelProviderResponse updateModelProvider(CurrentUser currentUser,
                                                     Long providerId,
                                                     ModelProviderUpdateRequest request) {
        requirePlatformAdmin(currentUser);
        ModelProviderEntity existingEntity = requireProvider(providerId);
        rejectReservedProviderCode(existingEntity.getProviderCode());
        existingEntity.setProviderName(request.getProviderName());
        existingEntity.setBaseUrl(request.getBaseUrl());
        existingEntity.setAuthMode(request.getAuthMode());
        if (request.getDefaultModel() != null) {
            existingEntity.setDefaultModel(request.getDefaultModel());
        }
        if (request.getPriority() != null) {
            existingEntity.setPriority(request.getPriority());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            existingEntity.setStatus(normalizeProviderStatus(request.getStatus()));
        }
        if (request.getCredentialSource() != null && !request.getCredentialSource().isBlank()) {
            CredentialSource source = CredentialSource.fromValue(request.getCredentialSource());
            if (routingConfigService.isRuleProvider(existingEntity) && source != CredentialSource.NONE) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "规则引擎供应商不支持凭据来源配置");
            }
            if (source == CredentialSource.ENCRYPTED_DB) {
                encryptionService.requireMasterKeyForEncryptedStorage();
            }
            existingEntity.setCredentialSource(source.code());
        }
        existingEntity.setUpdatedBy(currentUser.requiredUserId());
        existingEntity.setUpdatedAt(LocalDateTime.now());
        modelProviderEntityMapper.updateProviderAdmin(existingEntity);
        ModelProviderEntity refreshedEntity = modelProviderEntityMapper.findById(providerId);
        auditLogWriter.insert(buildAuditLog(currentUser.requiredUserId(), "MODEL_PROVIDER_UPDATE", refreshedEntity));
        cacheInvalidator.invalidateAfterProviderChange();
        return toResponse(refreshedEntity);
    }

    @Transactional
    public ModelProviderResponse updateModelProviderStatus(CurrentUser currentUser,
                                                           Long providerId,
                                                           ModelProviderStatusUpdateRequest request) {
        requirePlatformAdmin(currentUser);
        ModelProviderEntity existingEntity = requireProvider(providerId);
        String normalizedStatus = normalizeProviderStatus(request.getStatus());
        modelProviderEntityMapper.updateStatus(providerId, normalizedStatus, currentUser.requiredUserId());
        ModelProviderEntity refreshedEntity = modelProviderEntityMapper.findById(providerId);
        auditLogWriter.insert(buildAuditLog(currentUser.requiredUserId(), "MODEL_PROVIDER_STATUS_UPDATE", refreshedEntity));
        cacheInvalidator.invalidateAfterProviderChange();
        return toResponse(refreshedEntity);
    }

    @Transactional
    public ProviderCredentialResponse upsertProviderCredential(CurrentUser currentUser,
                                                               Long providerId,
                                                               ProviderCredentialUpsertRequest request) {
        requirePlatformAdmin(currentUser);
        ModelProviderEntity provider = requireProvider(providerId);
        if (routingConfigService.isRuleProvider(provider)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "规则引擎供应商不支持 API Key");
        }
        encryptionService.requireMasterKeyForEncryptedStorage();
        if (CredentialSource.fromValue(provider.getCredentialSource()) != CredentialSource.ENCRYPTED_DB) {
            provider.setCredentialSource(CredentialSource.ENCRYPTED_DB.code());
            provider.setUpdatedBy(currentUser.requiredUserId());
            provider.setUpdatedAt(LocalDateTime.now());
            modelProviderEntityMapper.updateProviderAdmin(provider);
        }
        CredentialEncryptionService.EncryptedPayload encrypted = encryptionService.encrypt(request.getApiKey().trim());
        String maskedHint = ProviderCatalogSupport.maskApiKey(request.getApiKey());
        LocalDateTime now = LocalDateTime.now();
        ModelProviderCredentialEntity existing = credentialEntityMapper.findActiveByProviderId(providerId);
        if (existing == null) {
            existing = credentialEntityMapper.findByProviderId(providerId);
        }
        if (existing == null) {
            ModelProviderCredentialEntity created = ModelProviderCredentialEntity.builder()
                    .providerId(providerId)
                    .credentialType("API_KEY")
                    .ciphertext(encrypted.ciphertext())
                    .nonce(encrypted.nonce())
                    .keyVersion(encrypted.keyVersion())
                    .maskedHint(maskedHint)
                    .createdAt(now)
                    .updatedAt(now)
                    .isDeleted(0)
                    .build();
            credentialEntityMapper.insertCredential(created);
        } else {
            existing.setCredentialType("API_KEY");
            existing.setCiphertext(encrypted.ciphertext());
            existing.setNonce(encrypted.nonce());
            existing.setKeyVersion(encrypted.keyVersion());
            existing.setMaskedHint(maskedHint);
            existing.setUpdatedAt(now);
            existing.setIsDeleted(0);
            credentialEntityMapper.upsertByProviderId(existing);
        }
        auditLogWriter.insert(buildAuditLog(currentUser.requiredUserId(), "MODEL_PROVIDER_CREDENTIAL_UPSERT", provider));
        cacheInvalidator.invalidateAfterProviderChange();
        return new ProviderCredentialResponse(true, CredentialSource.ENCRYPTED_DB.code(), maskedHint);
    }

    @Transactional
    public ProviderCredentialResponse deleteProviderCredential(CurrentUser currentUser, Long providerId) {
        requirePlatformAdmin(currentUser);
        ModelProviderEntity provider = requireProvider(providerId);
        if (routingConfigService.isRuleProvider(provider)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "规则引擎供应商不支持 API Key");
        }
        credentialEntityMapper.softDeleteByProviderId(providerId, LocalDateTime.now());
        auditLogWriter.insert(buildAuditLog(currentUser.requiredUserId(), "MODEL_PROVIDER_CREDENTIAL_DELETE", provider));
        cacheInvalidator.invalidateAfterProviderChange();
        return new ProviderCredentialResponse(false, provider.getCredentialSource(), null);
    }

    public ProviderHealthCheckResponse healthCheckModelProvider(CurrentUser currentUser, Long providerId) {
        requirePlatformAdmin(currentUser);
        ModelProviderEntity provider = requireProvider(providerId);
        ProviderHealthCheckService.ProviderHealthCheckResult result = healthCheckService.check(provider);
        return new ProviderHealthCheckResponse(
                provider.getId(),
                provider.getProviderCode(),
                result.healthy(),
                result.message(),
                LocalDateTime.now()
        );
    }

    private void requirePlatformAdmin(CurrentUser currentUser) {
        if (!currentUser.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private void rejectReservedProviderCode(String providerCode) {
        if (ProviderCatalogSupport.isReservedProviderCode(providerCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "保留编码不能作为供应商: " + providerCode);
        }
    }

    private void ensureProviderCodeUnique(Long providerId, String providerCode) {
        ModelProviderEntity existingEntity = modelProviderEntityMapper.findByProviderCode(providerCode);
        if (existingEntity != null && (providerId == null || !providerId.equals(existingEntity.getId()))) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "providerCode 已存在");
        }
    }

    private AuditLogEntity buildAuditLog(Long operatorUserId, String actionCode, ModelProviderEntity entity) {
        return AuditLogEntity.builder()
                .workspaceId(null)
                .actorUserId(operatorUserId)
                .actionCode(actionCode)
                .targetType("MODEL_PROVIDER")
                .targetId(String.valueOf(entity.getId()))
                .requestId(ResultUtils.currentRequestId())
                .detailJson(buildAuditDetail(entity))
                .build();
    }

    private String buildAuditDetail(ModelProviderEntity entity) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("providerId", entity.getId());
            detail.put("providerCode", entity.getProviderCode());
            detail.put("providerName", entity.getProviderName());
            detail.put("baseUrl", entity.getBaseUrl());
            detail.put("authMode", entity.getAuthMode());
            detail.put("status", entity.getStatus());
            detail.put("priority", entity.getPriority());
            detail.put("credentialSource", entity.getCredentialSource());
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Model provider audit detail serialization failed", exception);
        }
    }

    private ModelProviderEntity requireProvider(Long providerId) {
        ModelProviderEntity existingEntity = modelProviderEntityMapper.findById(providerId);
        if (existingEntity == null || ProviderCatalogSupport.isPseudoProvider(existingEntity)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, MODEL_PROVIDER_NOT_FOUND_MESSAGE);
        }
        return existingEntity;
    }

    private String normalizeProviderStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "status 非法");
        }
        String normalized = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "status 仅支持 ACTIVE / DISABLED");
        }
        if ("INACTIVE".equals(normalized)) {
            return "DISABLED";
        }
        return normalized;
    }

    private ModelProviderResponse toResponse(ModelProviderEntity entity) {
        CredentialSource source = CredentialSource.fromValue(entity.getCredentialSource());
        return new ModelProviderResponse(
                entity.getId(),
                entity.getProviderCode(),
                entity.getProviderName(),
                entity.getBaseUrl(),
                entity.getAuthMode(),
                entity.getSecretRef(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getDefaultModel(),
                source.code(),
                credentialResolver.isConfigured(entity),
                credentialResolver.maskedHint(entity),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
