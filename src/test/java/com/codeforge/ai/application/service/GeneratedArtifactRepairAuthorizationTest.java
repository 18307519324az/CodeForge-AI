package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.application.service.repair.RepairServiceTestFactory;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class GeneratedArtifactRepairAuthorizationTest {

    private static final Long APP_ID = 88003L;
    private static final Long SOURCE_VERSION_ID = 95L;
    private static final Long WORKSPACE_ID = 1001L;

    @TempDir
    java.nio.file.Path storageRoot;

    private GeneratedArtifactRepairApplicationService service;
    private WorkspaceAccessService workspaceAccessService;
    private GeneratedFileEntityMapper generatedFileEntityMapper;

    @BeforeEach
    void setUp() {
        AiAppEntityMapper aiAppEntityMapper = mock(AiAppEntityMapper.class);
        AppVersionEntityMapper appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        GeneratedArtifactRepairAuditService repairAuditService = mock(GeneratedArtifactRepairAuditService.class);
        service = RepairServiceTestFactory.createWithMockedCommit(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                workspaceAccessService,
                RepairServiceTestFactory.mockSuccessfulCommit());
        ReflectionTestUtils.setField(service, "storageRoot", storageRoot);

        WorkspaceEntity workspace = WorkspaceEntity.builder().id(WORKSPACE_ID).name("ws").build();
        given(workspaceAccessService.requireEditorAccess(any(), eq(WORKSPACE_ID))).willReturn(workspace);
        given(workspaceAccessService.requireReadAccess(any(), eq(WORKSPACE_ID))).willReturn(workspace);

        AiAppEntity app = AiAppEntity.builder().id(APP_ID).workspaceId(WORKSPACE_ID).build();
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
        AtomicInteger versionId = new AtomicInteger(97);
        doAnswer(invocation -> {
            AppVersionEntity version = invocation.getArgument(0);
            version.setId((long) versionId.getAndIncrement());
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any(AppVersionEntity.class));
    }

    @Test
    void OwnerCanRepairArtifactTest() {
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        var response = service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);
        assertThat(response.repairedVersionId()).isNotNull();
    }

    @Test
    void EditorCanRepairArtifactTest() {
        CurrentUser editor = new CurrentUser(2L, "editor@test", List.of("USER"));
        var response = service.repairArtifactVersion(editor, APP_ID, SOURCE_VERSION_ID);
        assertThat(response.repairedVersionId()).isNotNull();
    }

    @Test
    void ViewerCannotRepairArtifactTest() {
        CurrentUser viewer = new CurrentUser(3L, "viewer@test", List.of("USER"));
        given(workspaceAccessService.requireEditorAccess(viewer, WORKSPACE_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.repairArtifactVersion(viewer, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ForeignUserCannotRepairArtifactTest() {
        CurrentUser foreign = new CurrentUser(99L, "foreign@test", List.of("USER"));
        given(workspaceAccessService.requireEditorAccess(foreign, WORKSPACE_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.repairArtifactVersion(foreign, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void AnonymousCannotRepairArtifactTest() {
        CurrentUser anonymous = new CurrentUser(null, null, List.of());
        given(workspaceAccessService.requireEditorAccess(anonymous, WORKSPACE_ID))
                .willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> service.repairArtifactVersion(anonymous, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void AdminRepairFollowsExplicitContractTest() {
        CurrentUser admin = new CurrentUser(4L, "admin@test", List.of("PLATFORM_ADMIN"));
        var response = service.repairArtifactVersion(admin, APP_ID, SOURCE_VERSION_ID);
        verify(workspaceAccessService).requireEditorAccess(admin, WORKSPACE_ID);
        assertThat(response.repairedVersionId()).isNotNull();
    }
}
