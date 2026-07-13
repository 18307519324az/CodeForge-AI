package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.deploy.DeploymentCreateRequest;
import com.codeforge.ai.application.dto.deploy.DeploymentCreateResponse;
import com.codeforge.ai.application.dto.deploy.DeploymentDetailResponse;
import com.codeforge.ai.application.dto.deploy.DeploymentLogResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.deploy.entity.DeploymentJobEntity;
import com.codeforge.ai.domain.deploy.entity.DeploymentLogEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.DeploymentJobEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.DeploymentLogEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.ResultUtils;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeploymentApplicationService {

    private final AiAppEntityMapper aiAppEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final DeploymentJobEntityMapper deploymentJobEntityMapper;
    private final DeploymentLogEntityMapper deploymentLogEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;

    @Transactional
    public DeploymentCreateResponse createDeployment(CurrentUser currentUser, DeploymentCreateRequest request) {
        AiAppEntity appEntity = requireEditableApp(currentUser, request.getAppId());
        AppVersionEntity versionEntity = requireVersion(appEntity.getId(), request.getAppVersionId());
        String requestId = ResultUtils.currentRequestId();
        DeploymentJobEntity jobEntity = DeploymentJobEntity.builder()
                .appId(appEntity.getId())
                .appVersionId(versionEntity.getId())
                .environmentCode(request.getEnvironmentCode())
                .deployTarget(request.getDeployTarget())
                .deployStatus("QUEUED")
                .runtimeConfigJson(request.getRuntimeConfigJson())
                .requestId(requestId)
                .build();
        jobEntity.setCreatedBy(currentUser.requiredUserId());
        jobEntity.setUpdatedBy(currentUser.requiredUserId());
        deploymentJobEntityMapper.insert(jobEntity);

        DeploymentLogEntity logEntity = DeploymentLogEntity.builder()
                .deploymentJobId(jobEntity.getId())
                .logLevel("INFO")
                .logMessage("Deployment job queued")
                .logTime(LocalDateTime.now())
                .build();
        deploymentLogEntityMapper.insert(logEntity);

        return new DeploymentCreateResponse(
                jobEntity.getId(),
                jobEntity.getAppId(),
                jobEntity.getAppVersionId(),
                jobEntity.getEnvironmentCode(),
                jobEntity.getDeployTarget(),
                jobEntity.getDeployStatus(),
                jobEntity.getRequestId(),
                jobEntity.getCreatedAt()
        );
    }

    public DeploymentDetailResponse getDeployment(CurrentUser currentUser, Long deploymentJobId) {
        DeploymentJobEntity jobEntity = requireReadableJob(currentUser, deploymentJobId);
        return new DeploymentDetailResponse(
                jobEntity.getId(),
                jobEntity.getAppId(),
                jobEntity.getAppVersionId(),
                jobEntity.getEnvironmentCode(),
                jobEntity.getDeployTarget(),
                jobEntity.getDeployStatus(),
                jobEntity.getRuntimeConfigJson(),
                jobEntity.getRequestId(),
                jobEntity.getStartedAt(),
                jobEntity.getFinishedAt(),
                jobEntity.getCreatedAt(),
                jobEntity.getUpdatedAt()
        );
    }

    public List<DeploymentLogResponse> getDeploymentLogs(CurrentUser currentUser, Long deploymentJobId) {
        DeploymentJobEntity jobEntity = requireReadableJob(currentUser, deploymentJobId);
        return deploymentLogEntityMapper.findByDeploymentJobId(jobEntity.getId()).stream()
                .map(entity -> new DeploymentLogResponse(
                        entity.getId(),
                        entity.getDeploymentJobId(),
                        entity.getLogLevel(),
                        entity.getLogMessage(),
                        entity.getLogTime()
                ))
                .toList();
    }

    private AiAppEntity requireEditableApp(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireEditorAccess(currentUser, appEntity.getWorkspaceId());
        return appEntity;
    }

    private AppVersionEntity requireVersion(Long appId, Long versionId) {
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appId, versionId);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "App version does not exist");
        }
        return versionEntity;
    }

    private DeploymentJobEntity requireReadableJob(CurrentUser currentUser, Long deploymentJobId) {
        DeploymentJobEntity jobEntity = deploymentJobEntityMapper.findById(deploymentJobId);
        if (jobEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Deployment job does not exist");
        }
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(jobEntity.getAppId());
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireReadAccess(currentUser, appEntity.getWorkspaceId());
        return jobEntity;
    }
}
