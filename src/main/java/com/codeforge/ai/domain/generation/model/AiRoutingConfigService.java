package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.AiRoutingConfigEntity;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiRoutingConfigEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiRoutingConfigService {

    private final AiRoutingConfigEntityMapper routingConfigMapper;
    private final ModelProviderEntityMapper providerMapper;
    private final ModelProviderRoutingConfigService routingConfigService;
    private final ProviderCredentialResolver credentialResolver;

    @Value("${codeforge.ai.provider:${AI_PROVIDER:auto}}")
    private String envProviderConfig;

    public EffectiveRoutingConfig getEffectiveConfig() {
        AiRoutingConfigEntity persisted = routingConfigMapper.findById(AiRoutingConfigEntity.SINGLETON_ID);
        if (persisted == null) {
            return EffectiveRoutingConfig.fromEnv(envProviderConfig);
        }
        if (persisted.getUpdatedBy() == null) {
            return EffectiveRoutingConfig.fromEnv(envProviderConfig);
        }
        ProviderRoutingMode mode = ProviderRoutingMode.valueOf(persisted.getRoutingMode());
        return new EffectiveRoutingConfig(mode, persisted.getPinnedProviderCode(), true);
    }

    public AiRoutingAdminView getAdminView() {
        EffectiveRoutingConfig effective = getEffectiveConfig();
        List<String> candidates = routingConfigService.listActiveProvidersSorted().stream()
                .filter(provider -> !routingConfigService.isRuleProvider(provider))
                .filter(provider -> !ProviderCatalogSupport.isPseudoProvider(provider))
                .filter(credentialResolver::isConfigured)
                .map(ModelProviderEntity::getProviderCode)
                .toList();
        return new AiRoutingAdminView(
                effective.mode().name(),
                effective.pinnedProviderCode(),
                candidates,
                effective.adminPersisted()
        );
    }

    public AiRoutingAdminView updateAdminConfig(String mode, String pinnedProviderCode, Long operatorUserId) {
        ProviderRoutingMode routingMode = parseMode(mode);
        String normalizedPinned = null;
        if (routingMode == ProviderRoutingMode.PIN) {
            normalizedPinned = requirePinnedProviderCode(pinnedProviderCode);
        }
        AiRoutingConfigEntity existing = routingConfigMapper.findById(AiRoutingConfigEntity.SINGLETON_ID);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AiRoutingConfigEntity created = AiRoutingConfigEntity.builder()
                    .id(AiRoutingConfigEntity.SINGLETON_ID)
                    .routingMode(routingMode.name())
                    .pinnedProviderCode(normalizedPinned)
                    .updatedBy(operatorUserId)
                    .updatedAt(now)
                    .isDeleted(0)
                    .build();
            routingConfigMapper.insert(created);
        } else {
            existing.setRoutingMode(routingMode.name());
            existing.setPinnedProviderCode(normalizedPinned);
            existing.setUpdatedBy(operatorUserId);
            existing.setUpdatedAt(now);
            routingConfigMapper.updateConfig(existing);
        }
        routingConfigService.invalidateCache();
        return getAdminView();
    }

    private ProviderRoutingMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "routing mode 不能为空");
        }
        try {
            return ProviderRoutingMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "routing mode 仅支持 AUTO / PIN");
        }
    }

    private String requirePinnedProviderCode(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "PIN 模式必须指定 providerCode");
        }
        String normalized = providerCode.trim().toLowerCase();
        if (ProviderCatalogSupport.isReservedProviderCode(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能固定到保留编码: " + normalized);
        }
        ModelProviderEntity provider = providerMapper.findByProviderCode(normalized);
        if (provider == null || routingConfigService.isRuleProvider(provider)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "固定供应商不存在");
        }
        return normalized;
    }

    public record EffectiveRoutingConfig(
            ProviderRoutingMode mode,
            String pinnedProviderCode,
            boolean adminPersisted
    ) {
        public static EffectiveRoutingConfig fromEnv(String envProviderConfig) {
            ProviderRoutingMode mode = ProviderRoutingMode.fromConfigValue(envProviderConfig);
            String pinned = ProviderRoutingMode.pinnedProviderCode(envProviderConfig);
            return new EffectiveRoutingConfig(mode, pinned, false);
        }
    }

    public record AiRoutingAdminView(
            String mode,
            String pinnedProviderCode,
            List<String> effectiveCandidates,
            boolean adminPersisted
    ) {
    }
}
