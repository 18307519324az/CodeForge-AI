package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeforge.ai.application.dto.export.ExportPackageCreateRequest;
import com.codeforge.ai.application.dto.export.ExportPackageCreateResponse;
import com.codeforge.ai.application.dto.export.ExportPackageListItemResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

class MarketplaceStoragePathNonDisclosureTest {

    private static final Long APP_ID = 3001L;
    private static final Long VERSION_ID = 88L;
    private static final Long WORKSPACE_ID = 1001L;

    @TempDir
    Path tempDir;

    private ExportPackageApplicationService exportPackageApplicationService;
    private ExportPackageEntityMapper exportPackageEntityMapper;

    @BeforeEach
    void setUp() throws Exception {
        AiAppEntityMapper aiAppEntityMapper = mock(AiAppEntityMapper.class);
        AppVersionEntityMapper appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        GeneratedFileEntityMapper generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);

        exportPackageApplicationService = new ExportPackageApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                workspaceAccessService);
        ReflectionTestUtils.setField(exportPackageApplicationService, "exportRoot", tempDir.toString());

        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, VERSION_ID))
                .willReturn(AppVersionEntity.builder()
                        .id(VERSION_ID)
                        .appId(APP_ID)
                        .versionNo(1)
                        .build());
        Path generatedFile = tempDir.resolve("generated").resolve("index.html");
        Files.createDirectories(generatedFile.getParent());
        Files.writeString(generatedFile, "<html>ok</html>", StandardCharsets.UTF_8);
        given(generatedFileEntityMapper.findByAppVersionId(VERSION_ID)).willReturn(List.of(
                GeneratedFileEntity.builder()
                        .appVersionId(VERSION_ID)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileType("html")
                        .storagePath(generatedFile.toString())
                        .build()));
        org.mockito.Mockito.doAnswer(invocation -> {
            ExportPackageEntity entity = invocation.getArgument(0);
            entity.setId(9001L);
            entity.setCreatedAt(LocalDateTime.of(2026, 7, 11, 12, 0));
            return 1;
        }).when(exportPackageEntityMapper).insert(any(ExportPackageEntity.class));
    }

    @Test
    void ExportPackageCreateResponseDoesNotExposeStoragePathTest() {
        ExportPackageCreateResponse response = exportPackageApplicationService.createExportPackage(
                new CurrentUser(8L, "amns", List.of("USER")),
                createRequest());

        assertThat(response.fileName()).endsWith(".zip");
        assertThat(response.fileName()).doesNotContain("generated-exports");
        assertThat(response.fileName()).doesNotContain(tempDir.toString());
        assertThat(String.valueOf(response)).doesNotContain("storagePath");
    }

    @Test
    void ExportPackageListDoesNotExposeStoragePathTest() {
        ExportPackageEntity entity = ExportPackageEntity.builder()
                .id(9001L)
                .appId(APP_ID)
                .appVersionId(VERSION_ID)
                .packageType("ZIP")
                .status("READY")
                .storagePath(tempDir.resolve("secret").resolve("zip_v1.zip").toString())
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 11, 12, 1));
        given(exportPackageEntityMapper.findByAppId(APP_ID)).willReturn(List.of(entity));

        List<ExportPackageListItemResponse> responses = exportPackageApplicationService.listExportPackages(
                new CurrentUser(8L, "amns", List.of("USER")), APP_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().fileName()).isEqualTo("zip_v1.zip");
        assertThat(String.valueOf(responses.getFirst())).doesNotContain("secret");
    }

    @Test
    void AppVersionFileResponseDoesNotExposeInternalPathTest() {
        AppVersionApplicationService appVersionApplicationService = new AppVersionApplicationService(
                mock(AiAppEntityMapper.class),
                mock(com.codeforge.ai.infrastructure.persistence.mapper.ArtifactSnapshotEntityMapper.class),
                mock(AppVersionEntityMapper.class),
                mock(GeneratedFileEntityMapper.class),
                mock(WorkspaceAccessService.class));

        GeneratedFileEntity fileEntity = GeneratedFileEntity.builder()
                .id(1L)
                .appVersionId(VERSION_ID)
                .filePath("index.html")
                .fileName("index.html")
                .fileType("html")
                .storagePath("D:/internal/apps/secret/index.html")
                .contentHash("abc")
                .fileSize(12L)
                .build();

        Object response = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                appVersionApplicationService, "toFileResponse", fileEntity);

        assertThat(String.valueOf(response)).doesNotContain("D:/internal");
        assertThat(String.valueOf(response)).doesNotContain("storagePath");
    }

    @Test
    void PublicMarketplaceResponseDoesNotExposeStoragePathTest() {
        com.codeforge.ai.application.dto.publication.PublicAppDetailResponse detail =
                new com.codeforge.ai.application.dto.publication.PublicAppDetailResponse(
                        1L,
                        "demo-slug",
                        "title",
                        "desc",
                        "ADMIN_WEB",
                        "owner",
                        1,
                        "AI_DIRECT",
                        true,
                        true,
                        com.codeforge.ai.domain.app.enums.PublicationDownloadAvailability.AVAILABLE,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        0L,
                        0L);

        assertThat(String.valueOf(detail)).doesNotContain("storagePath");
        assertThat(String.valueOf(detail)).doesNotContain("generated-exports");
    }

    @Test
    void DownloadHeadersDoNotExposeStoragePathTest() throws Exception {
        Path zipPath = tempDir.resolve("apps").resolve("3001").resolve("versions").resolve("1");
        Files.createDirectories(zipPath);
        Path zipFile = zipPath.resolve("zip_v1_20260711120000.zip");
        Files.write(zipPath.resolve("zip_v1_20260711120000.zip"), "zip".getBytes(StandardCharsets.UTF_8));

        given(exportPackageEntityMapper.selectOneById(9001L)).willReturn(ExportPackageEntity.builder()
                .id(9001L)
                .appId(APP_ID)
                .appVersionId(VERSION_ID)
                .status("READY")
                .storagePath(zipFile.toString())
                .build());

        var response = exportPackageApplicationService.getPackagePath(
                new CurrentUser(8L, "amns", List.of("USER")), 9001L);
        String disposition = com.codeforge.ai.shared.util.DownloadResponseSupport.contentDispositionAttachment(
                com.codeforge.ai.shared.util.DownloadResponseSupport.safeAttachmentFilename(response));

        assertThat(disposition).contains("zip_v1_20260711120000.zip");
        assertThat(disposition).doesNotContain(tempDir.toString());
        assertThat(disposition).doesNotContain("apps\\3001");
    }

    private ExportPackageCreateRequest createRequest() {
        ExportPackageCreateRequest request = new ExportPackageCreateRequest();
        request.setAppId(APP_ID);
        request.setAppVersionId(VERSION_ID);
        request.setPackageType("ZIP");
        return request;
    }
}
