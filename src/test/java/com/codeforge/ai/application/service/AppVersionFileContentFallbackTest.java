package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ArtifactSnapshotEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ArtifactSnapshotEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AppVersionFileContentFallbackTest {

    private AiAppEntityMapper aiAppEntityMapper;
    private ArtifactSnapshotEntityMapper artifactSnapshotEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private AppVersionApplicationService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        artifactSnapshotEntityMapper = mock(ArtifactSnapshotEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        service = new AppVersionApplicationService(
                aiAppEntityMapper,
                artifactSnapshotEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                workspaceAccessService
        );

        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(3001L, 7001L)).willReturn(AppVersionEntity.builder()
                .id(7001L)
                .appId(3001L)
                .versionNo(1)
                .build());
    }

    @Test
    void shouldReturnFileContentWhenDatabaseContentExists() {
        given(generatedFileEntityMapper.findByAppVersionIdAndFilePath(7001L, "index.html"))
                .willReturn(GeneratedFileEntity.builder()
                        .id(8001L)
                        .appVersionId(7001L)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileType("html")
                        .storagePath(tempDir.resolve("missing/index.html").toString())
                        .fileContent("<html><body>db-fallback</body></html>")
                        .build());

        var response = service.getFileContent(
                new CurrentUser(2001L, "editor", List.of("USER")),
                3001L,
                7001L,
                "index.html"
        );

        assertThat(response.content()).contains("db-fallback");
    }

    @Test
    void shouldReturnDiskContentWhenDatabaseContentMissing() throws Exception {
        Path file = tempDir.resolve("disk/index.html");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "<html><body>disk-fallback</body></html>", StandardCharsets.UTF_8);
        given(generatedFileEntityMapper.findByAppVersionIdAndFilePath(7001L, "index.html"))
                .willReturn(GeneratedFileEntity.builder()
                        .id(8002L)
                        .appVersionId(7001L)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileType("html")
                        .storagePath(file.toString())
                        .fileContent(null)
                        .build());

        var response = service.getFileContent(
                new CurrentUser(2001L, "editor", List.of("USER")),
                3001L,
                7001L,
                "index.html"
        );

        assertThat(response.content()).contains("disk-fallback");
    }

    @Test
    void shouldReturnNotFoundWhenDatabaseAndDiskContentAreMissing() {
        given(generatedFileEntityMapper.findByAppVersionIdAndFilePath(7001L, "index.html"))
                .willReturn(GeneratedFileEntity.builder()
                        .id(8003L)
                        .appVersionId(7001L)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileType("html")
                        .storagePath(tempDir.resolve("missing/index.html").toString())
                        .fileContent(null)
                        .build());

        assertThatThrownBy(() -> service.getFileContent(
                new CurrentUser(2001L, "editor", List.of("USER")),
                3001L,
                7001L,
                "index.html"
        )).isInstanceOf(BusinessException.class);
    }
}
