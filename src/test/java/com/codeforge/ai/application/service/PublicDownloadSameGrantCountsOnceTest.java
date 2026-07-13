package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.security.DownloadAccessTokenService;
import com.codeforge.ai.infrastructure.security.DownloadGrantRedemptionService;
import com.codeforge.ai.infrastructure.security.JwtProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

class PublicDownloadSameGrantCountsOnceTest {

    @TempDir
    Path tempDir;

    private DownloadAccessTokenService downloadAccessTokenService;
    private DownloadGrantRedemptionService downloadGrantRedemptionService;
    private AppPublicationEntityMapper appPublicationEntityMapper;
    private ExportPackageEntityMapper exportPackageEntityMapper;
    private AiAppEntityMapper aiAppEntityMapper;
    private PublicDownloadApplicationService publicDownloadApplicationService;
    private String downloadToken;
    private Path zipPath;

    @BeforeEach
    void setUp() throws Exception {
        aiAppEntityMapper = org.mockito.Mockito.mock(AiAppEntityMapper.class);
        downloadAccessTokenService = new DownloadAccessTokenService(
                new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L),
                new MarketplacePublicationAccessGuard(aiAppEntityMapper));
        downloadGrantRedemptionService = new DownloadGrantRedemptionService();
        appPublicationEntityMapper = org.mockito.Mockito.mock(AppPublicationEntityMapper.class);
        exportPackageEntityMapper = org.mockito.Mockito.mock(ExportPackageEntityMapper.class);
        publicDownloadApplicationService = new PublicDownloadApplicationService(
                downloadAccessTokenService,
                downloadGrantRedemptionService,
                appPublicationEntityMapper,
                exportPackageEntityMapper);

        zipPath = tempDir.resolve("source.zip");
        Files.write(zipPath, new byte[] {1, 2, 3, 4});

        downloadToken = downloadAccessTokenService.createDownloadToken(9001L, 100L, 45L, 501L);
        when(exportPackageEntityMapper.selectOneById(501L)).thenReturn(
                ExportPackageEntity.builder()
                        .id(501L)
                        .appId(100L)
                        .appVersionId(45L)
                        .status("READY")
                        .storagePath(zipPath.toString())
                        .build());
        when(appPublicationEntityMapper.findActiveById(9001L)).thenReturn(
                com.codeforge.ai.domain.app.entity.AppPublicationEntity.builder()
                        .id(9001L)
                        .appId(100L)
                        .versionId(45L)
                        .status(com.codeforge.ai.domain.app.enums.AppPublicationStatus.PUBLISHED)
                        .allowDownload(true)
                        .build());
        when(aiAppEntityMapper.selectOneById(100L)).thenReturn(AiAppEntity.builder()
                .id(100L)
                .status("ACTIVE")
                .build());
    }

    @Test
    void sameGrantCountsOnce() throws Exception {
        ResponseEntity<Resource> first = publicDownloadApplicationService.downloadByToken(downloadToken);
        ResponseEntity<Resource> second = publicDownloadApplicationService.downloadByToken(downloadToken);

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        verify(appPublicationEntityMapper, times(1)).incrementDownloadCount(9001L);
    }
}
