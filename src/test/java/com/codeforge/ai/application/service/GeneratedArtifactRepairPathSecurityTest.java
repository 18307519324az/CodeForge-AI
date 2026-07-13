package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.codeforge.ai.shared.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class GeneratedArtifactRepairPathSecurityTest {

    private static final Long APP_ID = 88001L;
    private static final Long SOURCE_VERSION_ID = 95L;
    private static final Long WORKSPACE_ID = 1001L;

    @TempDir
    Path storageRoot;

    @TempDir
    Path outsideDir;

    private AiAppEntityMapper aiAppEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private GeneratedArtifactRepairAuditService repairAuditService;
    private GeneratedArtifactRepairApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        repairAuditService = mock(GeneratedArtifactRepairAuditService.class);
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

        AiAppEntity app = AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .currentVersionId(SOURCE_VERSION_ID)
                .build();
        AppVersionEntity sourceVersion = AppVersionEntity.builder()
                .id(SOURCE_VERSION_ID)
                .appId(APP_ID)
                .versionNo(95)
                .sourceTaskId(143L)
                .build();

        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(app);
        given(aiAppEntityMapper.selectForUpdateById(APP_ID)).willReturn(app);
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, SOURCE_VERSION_ID)).willReturn(sourceVersion);
        given(appVersionEntityMapper.findMaxVersionNo(APP_ID)).willReturn(95);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(storageRoot)) {
            try (Stream<Path> paths = Files.walk(storageRoot)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        Files.deleteIfExists(outsideDir.resolve("outside.html"));
    }

    @Test
    void RepairTraversalCreatesNoExternalFileTest() throws Exception {
        stubSourceFiles("../outside.html", "pwned");
        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));

        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);

        assertThat(Files.exists(outsideDir.resolve("outside.html"))).isFalse();
        assertThat(Files.exists(storageRoot.resolve("apps").resolve(String.valueOf(APP_ID)).resolve("versions"))).isFalse();
        verify(appVersionEntityMapper, never()).insertVersion(any());
        verify(aiAppEntityMapper, never()).updateCurrentVersionId(anyLong(), anyLong(), anyLong());
    }

    @Test
    void RepairRejectsSourceStoragePathOutsideVersionRootTest() throws Exception {
        Path sourceRoot = storageRoot.resolve("apps").resolve(String.valueOf(APP_ID))
                .resolve("versions").resolve(String.valueOf(SOURCE_VERSION_ID));
        Files.createDirectories(sourceRoot);
        Path outsideFile = outsideDir.resolve("evil.html");
        Files.writeString(outsideFile, "<html></html>", StandardCharsets.UTF_8);

        GeneratedFileEntity evil = GeneratedFileEntity.builder()
                .id(1L)
                .appVersionId(SOURCE_VERSION_ID)
                .filePath("index.html")
                .fileName("index.html")
                .storagePath(outsideFile.toString())
                .build();
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(evil));

        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        verify(appVersionEntityMapper, never()).insertVersion(any());
    }

    @Test
    void RepairDoesNotExposeSourceAbsolutePathTest() throws Exception {
        Path sourceRoot = storageRoot.resolve("apps").resolve(String.valueOf(APP_ID))
                .resolve("versions").resolve(String.valueOf(SOURCE_VERSION_ID));
        Files.createDirectories(sourceRoot);
        Path outsideFile = outsideDir.resolve("secret.html");
        Files.writeString(outsideFile, "<html></html>", StandardCharsets.UTF_8);

        GeneratedFileEntity evil = GeneratedFileEntity.builder()
                .id(1L)
                .appVersionId(SOURCE_VERSION_ID)
                .filePath("index.html")
                .fileName("index.html")
                .storagePath(outsideFile.toString())
                .build();
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(evil));

        CurrentUser owner = new CurrentUser(1L, "owner@test", List.of("USER"));
        assertThatThrownBy(() -> service.repairArtifactVersion(owner, APP_ID, SOURCE_VERSION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(outsideFile.toString()));
    }

    private void stubSourceFiles(String filePath, String content) {
        GeneratedFileEntity file = GeneratedFileEntity.builder()
                .id(1L)
                .appVersionId(SOURCE_VERSION_ID)
                .filePath(filePath)
                .fileName(Path.of(filePath).getFileName().toString())
                .fileType("text/html")
                .fileContent(content)
                .build();
        given(generatedFileEntityMapper.findByAppVersionId(SOURCE_VERSION_ID)).willReturn(List.of(file));
        org.mockito.Mockito.doAnswer(invocation -> {
            AppVersionEntity version = invocation.getArgument(0);
            version.setId(97L);
            return 1;
        }).when(appVersionEntityMapper).insertVersion(any(AppVersionEntity.class));
    }
}
