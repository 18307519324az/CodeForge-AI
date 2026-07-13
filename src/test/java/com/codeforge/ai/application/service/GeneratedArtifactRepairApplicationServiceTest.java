package com.codeforge.ai.application.service;

import com.codeforge.ai.application.service.repair.GeneratedArtifactRepairCommitService;
import com.codeforge.ai.application.service.repair.PreparedRepairedFile;
import com.codeforge.ai.application.service.repair.RepairCommitCommand;
import com.codeforge.ai.application.service.repair.RepairServiceTestFactory;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class GeneratedArtifactRepairApplicationServiceTest {

    private static final Long APP_ID = 2075859068169371648L;
    private static final Long SOURCE_VERSION_ID = 95L;
    private static final Long REPAIRED_VERSION_ID = 96L;

    private AiAppEntityMapper aiAppEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private GeneratedArtifactRepairAuditService repairAuditService;
    private GeneratedArtifactRepairCommitService repairCommitService;
    private GeneratedArtifactRepairApplicationService service;

    @TempDir
    Path isolatedStorageRoot;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        repairAuditService = mock(GeneratedArtifactRepairAuditService.class);
        repairCommitService = mock(GeneratedArtifactRepairCommitService.class);
        service = RepairServiceTestFactory.createWithMockedCommit(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                workspaceAccessService,
                repairCommitService);
        ReflectionTestUtils.setField(service, "storageRoot", isolatedStorageRoot);

        WorkspaceEntity workspace = WorkspaceEntity.builder().id(1001L).name("ws").build();
        given(workspaceAccessService.requireEditorAccess(any(), eq(1001L))).willReturn(workspace);
        given(workspaceAccessService.requireReadAccess(any(), eq(1001L))).willReturn(workspace);

        AiAppEntity app = AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(1001L)
                .name("Runtime App")
                .currentVersionId(SOURCE_VERSION_ID)
                .build();
        AppVersionEntity sourceVersion = AppVersionEntity.builder()
                .id(SOURCE_VERSION_ID)
                .appId(APP_ID)
                .versionNo(3)
                .sourceTaskId(143L)
                .build();

        String escapedHtml = "<!DOCTYPE html>\\n<html>\\n<head>\\n<title>Demo</title>\\n</head>\\n<body>\\n</body>\\n</html>";
        GeneratedFileEntity indexFile = GeneratedFileEntity.builder()
                .id(1L)
                .appVersionId(SOURCE_VERSION_ID)
                .filePath("index.html")
                .fileName("index.html")
                .fileType("text/html")
                .fileContent(escapedHtml)
                .build();

        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(app);
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, SOURCE_VERSION_ID)).willReturn(sourceVersion);
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(indexFile));
        when(repairCommitService.commit(any(RepairCommitCommand.class))).thenAnswer(invocation -> {
            RepairCommitCommand command = invocation.getArgument(0);
            return buildRepairResponse(command, REPAIRED_VERSION_ID);
        });
    }

    private com.codeforge.ai.application.dto.app.AppVersionRepairResponse buildRepairResponse(
            RepairCommitCommand command,
            long repairedVersionId) {
        return new com.codeforge.ai.application.dto.app.AppVersionRepairResponse(
                command.appId(),
                command.sourceVersion().getId(),
                command.sourceVersion().getVersionNo(),
                repairedVersionId,
                command.sourceVersion().getVersionNo() + 1,
                GeneratedArtifactRepairCommitService.MANUAL_REPAIR_SOURCE,
                command.preparedFiles().size(),
                true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(isolatedStorageRoot)) {
            try (var paths = Files.walk(isolatedStorageRoot)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    void RepairedVersionPreservesHistoricalTaskTest() {
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        var response = service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);

        assertThat(response.sourceVersionId()).isEqualTo(SOURCE_VERSION_ID);
        assertThat(response.versionSource()).isEqualTo(GeneratedArtifactRepairCommitService.MANUAL_REPAIR_SOURCE);
        ArgumentCaptor<RepairCommitCommand> commandCaptor = ArgumentCaptor.forClass(RepairCommitCommand.class);
        verify(repairCommitService).commit(commandCaptor.capture());
        assertThat(commandCaptor.getValue().sourceVersion().getSourceTaskId()).isEqualTo(143L);
    }

    @Test
    void RepairedVersionContainsNewMascotAssetTest() {
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);

        ArgumentCaptor<RepairCommitCommand> commandCaptor = ArgumentCaptor.forClass(RepairCommitCommand.class);
        verify(repairCommitService).commit(commandCaptor.capture());
        assertThat(commandCaptor.getValue().preparedFiles())
                .anyMatch(file -> BrandAssetReferenceRewriter.GENERATED_MASCOT_RELATIVE_PATH
                        .equals(file.relativePath()));
    }

    @Test
    void RepairedVersionIndexUsesRelativeMascotPathTest() {
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);

        ArgumentCaptor<RepairCommitCommand> commandCaptor = ArgumentCaptor.forClass(RepairCommitCommand.class);
        verify(repairCommitService).commit(commandCaptor.capture());
        PreparedRepairedFile repairedIndex = commandCaptor.getValue().preparedFiles().stream()
                .filter(file -> "index.html".equals(file.relativePath()))
                .findFirst()
                .orElseThrow();
        assertThat(repairedIndex.textContent()).contains("\n");
        assertThat(repairedIndex.textContent()).doesNotContain("\\n");
    }

    @Test
    void GeneratedHtmlDoesNotRenderLiteralBackslashNTest() {
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);

        ArgumentCaptor<RepairCommitCommand> commandCaptor = ArgumentCaptor.forClass(RepairCommitCommand.class);
        verify(repairCommitService).commit(commandCaptor.capture());
        PreparedRepairedFile repairedIndex = commandCaptor.getValue().preparedFiles().stream()
                .filter(file -> "index.html".equals(file.relativePath()))
                .findFirst()
                .orElseThrow();
        assertThat(repairedIndex.textContent().toLowerCase()).contains("<html");
        assertThat(repairedIndex.textContent()).doesNotContain("\\n");
    }

    @Test
    void GeneratedPreviewDoesNotReferenceLegacyMascotTest() {
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);

        ArgumentCaptor<RepairCommitCommand> commandCaptor = ArgumentCaptor.forClass(RepairCommitCommand.class);
        verify(repairCommitService).commit(commandCaptor.capture());
        PreparedRepairedFile repairedIndex = commandCaptor.getValue().preparedFiles().stream()
                .filter(file -> "index.html".equals(file.relativePath()))
                .findFirst()
                .orElseThrow();
        assertThat(repairedIndex.textContent().toLowerCase()).doesNotContain("aiavatar");
        assertThat(repairedIndex.textContent().toLowerCase()).doesNotContain("yupi");
    }

    @Test
    void RepairedVersionSetsCurrentVersionTest() {
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        var response = service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);
        assertThat(response.repairedVersionId()).isEqualTo(REPAIRED_VERSION_ID);
    }

    @Test
    void ForeignUserCannotReadRepairedArtifactTest() {
        CurrentUser foreignUser = new CurrentUser(99L, "foreign@test", List.of("USER"));
        given(workspaceAccessService.requireEditorAccess(foreignUser, 1001L))
                .willThrow(new BusinessException(com.codeforge.ai.shared.exception.ErrorCode.FORBIDDEN));
        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> service.repairArtifactVersion(foreignUser, APP_ID, SOURCE_VERSION_ID));
    }

    @Test
    void RepeatedRepairCreatesDistinctAuditedVersionsTest() {
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        AtomicLong nextVersionId = new AtomicLong(96L);
        when(repairCommitService.commit(any(RepairCommitCommand.class))).thenAnswer(invocation -> {
            RepairCommitCommand command = invocation.getArgument(0);
            return buildRepairResponse(command, nextVersionId.getAndIncrement());
        });

        var first = service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);
        var second = service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID);

        assertThat(first.repairedVersionId()).isNotEqualTo(second.repairedVersionId());
        verify(repairCommitService, times(2)).commit(any());
    }
}
