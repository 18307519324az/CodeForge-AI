package com.codeforge.ai.application.service.release;

import com.codeforge.ai.application.config.ReleaseGatesProperties;
import com.codeforge.ai.application.dto.admin.PromptRuntimeBindingGateResponse;
import com.codeforge.ai.application.service.release.PromptFingerprintVerificationService.PromptFingerprintVerificationResult;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPromptRuntimeGateApplicationService {

    private final ReleaseGatesProperties releaseGatesProperties;
    private final PromptFingerprintVerificationService promptFingerprintVerificationService;
    private final PromptRuntimeArtifactResolver promptRuntimeArtifactResolver;
    private final GenerationTaskEntityMapper generationTaskEntityMapper;

    public PromptRuntimeBindingGateResponse verifyPromptRuntimeBinding(CurrentUser currentUser,
                                                                       Long taskId,
                                                                       Long modelCallId) {
        requirePlatformAdmin(currentUser);
        requireReleaseGatesEnabled();
        GenerationTaskEntity task = generationTaskEntityMapper.selectOneById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生成任务不存在");
        }
        PromptFingerprintVerificationResult verification =
                promptFingerprintVerificationService.verify(taskId, modelCallId);
        TaskArtifactBindingResult artifactBinding =
                promptRuntimeArtifactResolver.resolve(taskId, task.getAppId());
        return new PromptRuntimeBindingGateResponse(
                taskId,
                artifactBinding.appId(),
                artifactBinding.appVersionId(),
                artifactBinding.bindingSource(),
                artifactBinding.resolved(),
                artifactBinding.errorCode(),
                verification.modelCallId(),
                verification.generationSource(),
                verification.attemptPhase(),
                verification.pinnedTemplateVersionId(),
                verification.matchesPinnedVersion(),
                verification.matchesLatestVersion(),
                verification.systemHashMatches(),
                verification.userHashMatches(),
                verification.combinedMatches(),
                verification.systemHashMatches(),
                verification.userHashMatches(),
                verification.combinedMatches(),
                verification.latestSystemHashMatches(),
                verification.latestUserHashMatches(),
                verification.latestCombinedMatches());
    }

    private void requirePlatformAdmin(CurrentUser currentUser) {
        if (!currentUser.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private void requireReleaseGatesEnabled() {
        if (!releaseGatesProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Release gate API is disabled");
        }
    }
}
