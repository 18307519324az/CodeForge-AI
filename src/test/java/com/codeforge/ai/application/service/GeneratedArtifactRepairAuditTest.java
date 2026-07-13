package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AuditLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.application.service.repair.RepairServiceTestFactory;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GeneratedArtifactRepairAuditTest {

    private static final Long APP_ID = 88004L;
    private static final Long SOURCE_VERSION_ID = 95L;

    @TempDir
    java.nio.file.Path storageRoot;

    private GeneratedArtifactRepairApplicationService service;
    private GeneratedArtifactRepairAuditService repairAuditService;
    private AuditLogEntityMapper auditLogEntityMapper;

    @BeforeEach
    void setUp() {
        AiAppEntityMapper aiAppEntityMapper = mock(AiAppEntityMapper.class);
        AppVersionEntityMapper appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        GeneratedFileEntityMapper generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);
        auditLogEntityMapper = mock(AuditLogEntityMapper.class);
        repairAuditService =
                new GeneratedArtifactRepairAuditService(new AuditLogWriter(auditLogEntityMapper), new ObjectMapper());
        service = RepairServiceTestFactory.createWithMockedCommit(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                workspaceAccessService,
                RepairServiceTestFactory.mockSuccessfulCommit());
        ReflectionTestUtils.setField(service, "storageRoot", storageRoot);

        WorkspaceEntity workspace = WorkspaceEntity.builder().id(1001L).name("ws").build();
        given(workspaceAccessService.requireEditorAccess(any(), eq(1001L))).willReturn(workspace);
        given(workspaceAccessService.requireReadAccess(any(), eq(1001L))).willReturn(workspace);
        AiAppEntity app = AiAppEntity.builder().id(APP_ID).workspaceId(1001L).build();
        AppVersionEntity sourceVersion = AppVersionEntity.builder()
                .id(SOURCE_VERSION_ID).appId(APP_ID).versionNo(95).sourceTaskId(143L).build();
        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(app);
        given(aiAppEntityMapper.selectForUpdateById(APP_ID)).willReturn(app);
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, SOURCE_VERSION_ID)).willReturn(sourceVersion);
        given(appVersionEntityMapper.findMaxVersionNo(APP_ID)).willReturn(95);
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(
                GeneratedFileEntity.builder()
                        .id(1L)
                        .appVersionId(SOURCE_VERSION_ID)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileContent("<!DOCTYPE html>\\n<html>\\n<body></body>\\n</html>")
                        .build()));
        org.mockito.Mockito.doAnswer(invocation -> {
            AppVersionEntity version = invocation.getArgument(0);
            version.setId(97L);
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any(AppVersionEntity.class));
    }

    @Test
    void RepairWritesAuditTest() {
        repairAuditService.recordSuccessfulRepair(1L, APP_ID, SOURCE_VERSION_ID, 97L, 96);
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo(GeneratedArtifactRepairAuditService.ACTION_ARTIFACT_REPAIR);
        assertThat(captor.getValue().getActorUserId()).isEqualTo(1L);
    }

    @Test
    void RepairAuditCreatedAtIsNotNullTest() {
        repairAuditService.recordSuccessfulRepair(1L, APP_ID, SOURCE_VERSION_ID, 97L, 96);
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void RepairAuditDoesNotContainFileContentTest() {
        repairAuditService.recordSuccessfulRepair(1L, APP_ID, SOURCE_VERSION_ID, 97L, 96);
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getDetailJson()).doesNotContain("<html");
        assertThat(captor.getValue().getDetailJson()).doesNotContain("DOCTYPE");
    }

    @Test
    void RepairAuditDoesNotContainAbsolutePathTest() {
        repairAuditService.recordSuccessfulRepair(1L, APP_ID, SOURCE_VERSION_ID, 97L, 96);
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getDetailJson()).doesNotContain(storageRoot.toString());
        assertThat(captor.getValue().getDetailJson()).doesNotContain("storagePath");
    }

    @Test
    void RepairAuditDoesNotContainTokenOrAuthorizationTest() {
        repairAuditService.recordSuccessfulRepair(1L, APP_ID, SOURCE_VERSION_ID, 97L, 96);
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogEntityMapper).insert(captor.capture());
        String detail = captor.getValue().getDetailJson().toLowerCase();
        assertThat(detail).doesNotContain("authorization");
        assertThat(detail).doesNotContain("cookie");
        assertThat(detail).doesNotContain("password");
    }

    @Test
    void FailedRepairWritesNoSuccessAuditTest() {
        GeneratedFileEntityMapper generatedFileEntityMapper =
                (GeneratedFileEntityMapper) ReflectionTestUtils.getField(service, "generatedFileEntityMapper");
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(
                GeneratedFileEntity.builder()
                        .id(1L)
                        .appVersionId(SOURCE_VERSION_ID)
                        .filePath("../evil.html")
                        .fileName("evil.html")
                        .fileContent("x")
                        .build()));
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class);
        verify(auditLogEntityMapper, never()).insert(any());
    }
}
