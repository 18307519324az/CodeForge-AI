package com.codeforge.ai.application.service;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExportPackageApplicationServiceTest {

    private AiAppEntityMapper aiAppEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private ExportPackageEntityMapper exportPackageEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private ExportPackageApplicationService exportPackageApplicationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        exportPackageApplicationService = new ExportPackageApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                workspaceAccessService
        );
        ReflectionTestUtils.setField(exportPackageApplicationService, "exportRoot", tempDir.toString());
    }

    @Test
    void shouldCreateZipExportPackage() throws Exception {
        Path generatedFile = tempDir.resolve("generated").resolve("result.md");
        Files.createDirectories(generatedFile.getParent());
        Files.writeString(generatedFile, "# hello", StandardCharsets.UTF_8);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(3001L, 7001L)).willReturn(AppVersionEntity.builder()
                .id(7001L)
                .appId(3001L)
                .versionNo(1)
                .build());
        given(generatedFileEntityMapper.findByAppVersionId(7001L)).willReturn(List.of(
                GeneratedFileEntity.builder()
                        .appVersionId(7001L)
                        .filePath("apps/3001/versions/1/result.md")
                        .fileName("result.md")
                        .fileType("markdown")
                        .storagePath(generatedFile.toString())
                        .build()
        ));
        AtomicLong idSequence = new AtomicLong(9001L);
        doAnswer(invocation -> {
            ExportPackageEntity entity = invocation.getArgument(0);
            entity.setId(idSequence.getAndIncrement());
            entity.setCreatedAt(LocalDateTime.of(2026, 6, 23, 23, 58));
            return 1;
        }).when(exportPackageEntityMapper).insert(org.mockito.ArgumentMatchers.any(ExportPackageEntity.class));

        ExportPackageCreateRequest request = new ExportPackageCreateRequest();
        request.setAppId(3001L);
        request.setAppVersionId(7001L);
        request.setPackageType("ZIP");

        ExportPackageCreateResponse response = exportPackageApplicationService.createExportPackage(
                new CurrentUser(2001L, "editor", List.of("USER")),
                request
        );

        org.mockito.ArgumentCaptor<ExportPackageEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ExportPackageEntity.class);
        verify(exportPackageEntityMapper).insert(captor.capture());
        Path zipPath = Path.of(captor.getValue().getStoragePath());
        assertThat(Files.exists(zipPath)).isTrue();
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            assertThat(zipFile.getEntry("apps/3001/versions/1/result.md")).isNotNull();
        }
        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.versionNo()).isEqualTo(1);
        assertThat(response.fileName()).isNotBlank();
        verify(aiAppEntityMapper).selectOneById(3001L);
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldListExportPackages() {
        ExportPackageEntity exportPackageEntity = ExportPackageEntity.builder()
                .id(9001L)
                .appId(3001L)
                .appVersionId(7001L)
                .packageType("ZIP")
                .status("READY")
                .storagePath(tempDir.resolve("demo.zip").toString())
                .build();
        exportPackageEntity.setCreatedAt(LocalDateTime.of(2026, 6, 23, 23, 59));
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .build());
        given(exportPackageEntityMapper.findByAppId(3001L)).willReturn(List.of(exportPackageEntity));

        List<ExportPackageListItemResponse> response = exportPackageApplicationService.listExportPackages(
                new CurrentUser(2001L, "viewer", List.of("USER")),
                3001L
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().packageType()).isEqualTo("ZIP");
        assertThat(response.getFirst().status()).isEqualTo("READY");
    }
}
