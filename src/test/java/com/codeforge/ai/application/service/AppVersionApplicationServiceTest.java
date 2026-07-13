package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AppVersionFileContentResponse;
import com.codeforge.ai.application.dto.app.AppVersionDiffResponse;
import com.codeforge.ai.application.dto.app.AppVersionRollbackResponse;
import com.codeforge.ai.application.dto.app.AppVersionListItemResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ArtifactSnapshotEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ArtifactSnapshotEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.PageResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AppVersionApplicationServiceTest {

    private AiAppEntityMapper aiAppEntityMapper;
    private ArtifactSnapshotEntityMapper artifactSnapshotEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private AppVersionApplicationService appVersionApplicationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        artifactSnapshotEntityMapper = mock(ArtifactSnapshotEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        appVersionApplicationService = new AppVersionApplicationService(
                aiAppEntityMapper,
                artifactSnapshotEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                workspaceAccessService
        );
    }

    @Test
    void shouldListVersionsWithPagination() {
        AppVersionEntity version2 = AppVersionEntity.builder()
                .id(7002L)
                .appId(3001L)
                .versionNo(2)
                .versionSource("WEB_APP")
                .build();
        version2.setCreatedAt(LocalDateTime.of(2026, 6, 22, 22, 0));
        AppVersionEntity version1 = AppVersionEntity.builder()
                .id(7001L)
                .appId(3001L)
                .versionNo(1)
                .versionSource("WEB_APP")
                .build();
        version1.setCreatedAt(LocalDateTime.of(2026, 6, 22, 21, 0));
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(appVersionEntityMapper.findByAppId(3001L)).willReturn(List.of(version2, version1));
        PageRequest request = new PageRequest();
        request.setPageNo(1);
        request.setPageSize(1);

        PageResponse<AppVersionListItemResponse> response = appVersionApplicationService.listVersions(
                new CurrentUser(2001L, "editor", List.of("USER")),
                3001L,
                request
        );

        verify(workspaceAccessService).requireReadAccess(new CurrentUser(2001L, "editor", List.of("USER")), 1001L);
        assertThat(response.records()).hasSize(1);
        assertThat(response.total()).isEqualTo(2);
        assertThat(response.records().getFirst().versionNo()).isEqualTo(2);
    }

    @Test
    void shouldReadVersionFileContent() throws Exception {
        Path file = tempDir.resolve("result.md");
        Files.writeString(file, "# hello", StandardCharsets.UTF_8);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(3001L, 7001L)).willReturn(AppVersionEntity.builder()
                .id(7001L)
                .appId(3001L)
                .versionNo(1)
                .build());
        given(generatedFileEntityMapper.findByAppVersionIdAndFilePath(7001L, "apps/3001/versions/1/result.md"))
                .willReturn(GeneratedFileEntity.builder()
                        .id(8001L)
                        .appVersionId(7001L)
                        .filePath("apps/3001/versions/1/result.md")
                        .fileName("result.md")
                        .fileType("markdown")
                        .storagePath(file.toString())
                        .build());

        AppVersionFileContentResponse response = appVersionApplicationService.getFileContent(
                new CurrentUser(2001L, "editor", List.of("USER")),
                3001L,
                7001L,
                "apps/3001/versions/1/result.md"
        );

        assertThat(response.content()).isEqualTo("# hello");
        assertThat(response.fileType()).isEqualTo("markdown");
    }

    @Test
    void shouldDiffVersionFiles() {
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(3001L, 7001L)).willReturn(AppVersionEntity.builder()
                .id(7001L)
                .appId(3001L)
                .versionNo(1)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(3001L, 7002L)).willReturn(AppVersionEntity.builder()
                .id(7002L)
                .appId(3001L)
                .versionNo(2)
                .build());
        given(artifactSnapshotEntityMapper.findLatestByAppVersionIdAndSnapshotType(7001L, "FILE_TREE"))
                .willReturn(ArtifactSnapshotEntity.builder().id(8101L).appVersionId(7001L).snapshotType("FILE_TREE").contentHash("hash_v1").build());
        given(artifactSnapshotEntityMapper.findLatestByAppVersionIdAndSnapshotType(7002L, "FILE_TREE"))
                .willReturn(ArtifactSnapshotEntity.builder().id(8102L).appVersionId(7002L).snapshotType("FILE_TREE").contentHash("hash_v2").build());
        given(generatedFileEntityMapper.findByAppVersionId(7001L)).willReturn(List.of(
                GeneratedFileEntity.builder().appVersionId(7001L).filePath("a.md").contentHash("hash_a_1").fileSize(10L).build(),
                GeneratedFileEntity.builder().appVersionId(7001L).filePath("b.md").contentHash("hash_b_1").fileSize(20L).build()
        ));
        given(generatedFileEntityMapper.findByAppVersionId(7002L)).willReturn(List.of(
                GeneratedFileEntity.builder().appVersionId(7002L).filePath("a.md").contentHash("hash_a_2").fileSize(11L).build(),
                GeneratedFileEntity.builder().appVersionId(7002L).filePath("c.md").contentHash("hash_c_1").fileSize(30L).build()
        ));

        AppVersionDiffResponse response = appVersionApplicationService.diffVersions(
                new CurrentUser(2001L, "editor", List.of("USER")),
                3001L,
                7001L,
                7002L
        );

        assertThat(response.fromSnapshotHash()).isEqualTo("hash_v1");
        assertThat(response.toSnapshotHash()).isEqualTo("hash_v2");
        assertThat(response.changedFiles()).hasSize(3);
        assertThat(response.changedFiles().get(0).changeType()).isEqualTo("MODIFIED");
        assertThat(response.changedFiles().get(1).changeType()).isEqualTo("REMOVED");
        assertThat(response.changedFiles().get(2).changeType()).isEqualTo("ADDED");
    }

    @Test
    void shouldRollbackCurrentVersion() {
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(3001L, 7002L)).willReturn(AppVersionEntity.builder()
                .id(7002L)
                .appId(3001L)
                .versionNo(2)
                .build());

        AppVersionRollbackResponse response = appVersionApplicationService.rollbackVersion(
                new CurrentUser(2001L, "editor", List.of("USER")),
                3001L,
                7002L
        );

        assertThat(response.status()).isEqualTo("ROLLED_BACK");
        assertThat(response.versionNo()).isEqualTo(2);
        verify(aiAppEntityMapper, times(1)).updateCurrentVersionId(3001L, 7002L, 2001L);
    }
}
