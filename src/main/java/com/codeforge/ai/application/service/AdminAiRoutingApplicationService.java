package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.AiRoutingConfigResponse;
import com.codeforge.ai.application.dto.admin.AiRoutingConfigUpdateRequest;
import com.codeforge.ai.domain.generation.model.AiRoutingConfigService;
import com.codeforge.ai.domain.generation.model.ProviderConfigCacheInvalidator;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAiRoutingApplicationService {

    private final AiRoutingConfigService aiRoutingConfigService;
    private final ProviderConfigCacheInvalidator cacheInvalidator;

    public AiRoutingConfigResponse getAiRouting(CurrentUser currentUser) {
        requirePlatformAdmin(currentUser);
        return toResponse(aiRoutingConfigService.getAdminView());
    }

    @Transactional
    public AiRoutingConfigResponse updateAiRouting(CurrentUser currentUser, AiRoutingConfigUpdateRequest request) {
        requirePlatformAdmin(currentUser);
        AiRoutingConfigService.AiRoutingAdminView view = aiRoutingConfigService.updateAdminConfig(
                request.getMode(),
                request.getProviderCode(),
                currentUser.requiredUserId());
        cacheInvalidator.invalidateAfterProviderChange();
        return toResponse(view);
    }

    private AiRoutingConfigResponse toResponse(AiRoutingConfigService.AiRoutingAdminView view) {
        return new AiRoutingConfigResponse(
                view.mode(),
                view.pinnedProviderCode(),
                view.effectiveCandidates(),
                view.adminPersisted()
        );
    }

    private void requirePlatformAdmin(CurrentUser currentUser) {
        if (!currentUser.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }
}
