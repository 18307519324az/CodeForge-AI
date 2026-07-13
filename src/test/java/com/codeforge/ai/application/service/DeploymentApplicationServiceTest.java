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
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeploymentApplicationServiceTest {

    private AiAppEntityMapper aiAppEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private DeploymentJobEntityMapper deploymentJobEntityMapper;
    private DeploymentLogEntityMapper deploymentLogEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private DeploymentApplicationService deploymentApplicationService;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        deploymentJobEntityMapper = mock(DeploymentJobEntityMapper.class);
        deploymentLogEntityMapper = mock(DeploymentLogEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        deploymentApplicationService = new DeploymentApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                deploymentJobEntityMapper,
                deploymentLogEntityMapper,
                workspaceAccessService
        );
    }

    @Test
    void shouldCreateDeploymentJobAndLog() {
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(3001L, 7001L)).willReturn(AppVersionEntity.builder()
                .id(7001L)
                .appId(3001L)
                .versionNo(1)
                .build());
        AtomicLong jobId = new AtomicLong(9101L);
        doAnswer(invocation -> {
            DeploymentJobEntity entity = invocation.getArgument(0);
            entity.setId(jobId.getAndIncrement());
            entity.setCreatedAt(LocalDateTime.of(2026, 6, 24, 0, 5));
            return 1;
        }).when(deploymentJobEntityMapper).insert(any(DeploymentJobEntity.class));

        DeploymentCreateRequest request = new DeploymentCreateRequest();
        request.setAppId(3001L);
        request.setAppVersionId(7001L);
        request.setEnvironmentCode("prod");
        request.setDeployTarget("docker");
        request.setRuntimeConfigJson("{\"replicas\":1}");

        DeploymentCreateResponse response = deploymentApplicationService.createDeployment(
                new CurrentUser(2001L, "editor", List.of("USER")),
                request
        );

        assertThat(response.id()).isEqualTo(9101L);
        assertThat(response.deployStatus()).isEqualTo("QUEUED");
        assertThat(response.requestId()).isNotBlank();
        verify(deploymentJobEntityMapper).insert(any(DeploymentJobEntity.class));
        verify(deploymentLogEntityMapper).insert(any(DeploymentLogEntity.class));
    }

    @Test
    void shouldReturnDeploymentDetailAndLogs() {
        given(deploymentJobEntityMapper.findById(9101L)).willReturn(DeploymentJobEntity.builder()
                .id(9101L)
                .appId(3001L)
                .appVersionId(7001L)
                .environmentCode("prod")
                .deployTarget("docker")
                .deployStatus("QUEUED")
                .runtimeConfigJson("{\"replicas\":1}")
                .requestId("req_demo")
                .startedAt(null)
                .finishedAt(null)
                .build());
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(deploymentLogEntityMapper.findByDeploymentJobId(9101L)).willReturn(List.of(
                DeploymentLogEntity.builder()
                        .id(9201L)
                        .deploymentJobId(9101L)
                        .logLevel("INFO")
                        .logMessage("Deployment job queued")
                        .logTime(LocalDateTime.of(2026, 6, 24, 0, 6))
                        .build()
        ));

        DeploymentDetailResponse detailResponse = deploymentApplicationService.getDeployment(
                new CurrentUser(2001L, "viewer", List.of("USER")),
                9101L
        );
        List<DeploymentLogResponse> logResponses = deploymentApplicationService.getDeploymentLogs(
                new CurrentUser(2001L, "viewer", List.of("USER")),
                9101L
        );

        assertThat(detailResponse.deployTarget()).isEqualTo("docker");
        assertThat(logResponses).hasSize(1);
        assertThat(logResponses.getFirst().logMessage()).isEqualTo("Deployment job queued");
    }
}
