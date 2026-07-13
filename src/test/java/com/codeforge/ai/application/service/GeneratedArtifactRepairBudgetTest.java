package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codeforge.ai.application.service.repair.RepairServiceTestFactory;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactBudget;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class GeneratedArtifactRepairBudgetTest {

    private static final Long APP_ID = 88002L;
    private static final Long SOURCE_VERSION_ID = 95L;

    @TempDir
    java.nio.file.Path storageRoot;

    private GeneratedArtifactRepairApplicationService service;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;

    @BeforeEach
    void setUp() {
        AiAppEntityMapper aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);
        GeneratedArtifactRepairAuditService repairAuditService = mock(GeneratedArtifactRepairAuditService.class);
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
    }

    @Test
    void RepairRejectsTooManyFilesTest() {
        List<GeneratedFileEntity> files = new ArrayList<>();
        for (int index = 0; index < GeneratedArtifactBudget.MAX_FILE_COUNT + 1; index++) {
            files.add(GeneratedFileEntity.builder()
                    .id((long) index)
                    .appVersionId(SOURCE_VERSION_ID)
                    .filePath("file-" + index + ".txt")
                    .fileName("file-" + index + ".txt")
                    .fileContent("ok")
                    .build());
        }
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(files);
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
        verify(appVersionEntityMapper, never()).insertVersion(any());
    }

    @Test
    void RepairRejectsOversizedSingleFileTest() {
        String huge = "x".repeat((int) GeneratedArtifactBudget.MAX_SINGLE_TEXT_FILE_BYTES + 1);
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(
                GeneratedFileEntity.builder()
                        .id(1L)
                        .appVersionId(SOURCE_VERSION_ID)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileContent(huge)
                        .build()));
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
    }

    @Test
    void RepairRejectsOversizedTotalArtifactTest() {
        int perFile = (int) (GeneratedArtifactBudget.MAX_TOTAL_TEXT_BYTES / 2) + 1;
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(
                file("a.html", "a".repeat(perFile)),
                file("b.html", "b".repeat(perFile))));
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
    }

    @Test
    void RepairBudgetFailureWritesNothingTest() {
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(
                file("index.html", "x".repeat((int) GeneratedArtifactBudget.MAX_SINGLE_TEXT_FILE_BYTES + 1))));
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class);
        verify(appVersionEntityMapper, never()).insertVersion(any());
    }

    @Test
    void MascotAssetCountsTowardArtifactBudgetTest() {
        String almostFull = "x".repeat((int) GeneratedArtifactBudget.MAX_TOTAL_TEXT_BYTES);
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(
                file("index.html", almostFull)));
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
    }

    private GeneratedFileEntity file(String path, String content) {
        return GeneratedFileEntity.builder()
                .id(1L)
                .appVersionId(SOURCE_VERSION_ID)
                .filePath(path)
                .fileName(path)
                .fileContent(content)
                .build();
    }
}
