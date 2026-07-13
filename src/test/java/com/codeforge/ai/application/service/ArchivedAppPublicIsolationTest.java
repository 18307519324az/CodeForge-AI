package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.domain.app.enums.AiAppStatus;
import com.codeforge.ai.domain.app.enums.AppPublicationStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.security.DownloadAccessTokenService;
import com.codeforge.ai.infrastructure.security.DownloadGrantRedemptionService;
import com.codeforge.ai.infrastructure.security.JwtProperties;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

class ArchivedAppPublicIsolationTest {

    private static final Long APP_ID = 3001L;
    private static final Long VERSION_ID = 88L;
    private static final Long PUBLICATION_ID = 9101L;
    private static final Long PACKAGE_ID = 501L;
    private static final String SLUG = "archived-published-slug";

    @TempDir
    Path tempDir;

    private AiAppEntityMapper aiAppEntityMapper;
    private AppPublicationEntityMapper appPublicationEntityMapper;
    private ExportPackageEntityMapper exportPackageEntityMapper;
    private MarketplacePublicationAccessGuard marketplacePublicationAccessGuard;
    private AppPublicationApplicationService appPublicationApplicationService;
    private PublicAppApplicationService publicAppApplicationService;
    private DownloadAccessTokenService downloadAccessTokenService;
    private PreviewAccessTokenService previewAccessTokenService;
    private PublicDownloadApplicationService publicDownloadApplicationService;
    private String downloadToken;
    private String previewToken;

    @BeforeEach
    void setUp() throws Exception {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appPublicationEntityMapper = mock(AppPublicationEntityMapper.class);
        exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        marketplacePublicationAccessGuard = new MarketplacePublicationAccessGuard(aiAppEntityMapper);

        appPublicationApplicationService = new AppPublicationApplicationService(
                aiAppEntityMapper,
                mock(com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper.class),
                appPublicationEntityMapper,
                mock(com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper.class),
                exportPackageEntityMapper,
                mock(WorkspaceAccessService.class),
                mock(VueProjectBuildService.class),
                marketplacePublicationAccessGuard,
                mock(MarketplaceAuditService.class));

        publicAppApplicationService = new PublicAppApplicationService(
                appPublicationEntityMapper,
                appPublicationApplicationService,
                aiAppEntityMapper,
                mock(com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper.class),
                mock(com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper.class),
                exportPackageEntityMapper,
                new PreviewAccessTokenService(
                        new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L),
                        marketplacePublicationAccessGuard),
                new DownloadAccessTokenService(
                        new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L),
                        marketplacePublicationAccessGuard),
                mock(com.codeforge.ai.infrastructure.persistence.mapper.PublicationViewDedupeEntityMapper.class),
                mock(com.codeforge.ai.infrastructure.security.PublicationViewerIdentityService.class));

        downloadAccessTokenService = new DownloadAccessTokenService(
                new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L),
                marketplacePublicationAccessGuard);
        previewAccessTokenService = new PreviewAccessTokenService(
                new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L),
                marketplacePublicationAccessGuard);
        publicDownloadApplicationService = new PublicDownloadApplicationService(
                downloadAccessTokenService,
                new DownloadGrantRedemptionService(),
                appPublicationEntityMapper,
                exportPackageEntityMapper);

        stubArchivedPublishedState();
        downloadToken = downloadAccessTokenService.createDownloadToken(
                PUBLICATION_ID, APP_ID, VERSION_ID, PACKAGE_ID);
        previewToken = previewAccessTokenService.createPublicPreviewToken(
                PUBLICATION_ID, APP_ID, VERSION_ID);
    }

    @Test
    void ArchivedAppIsExcludedFromMarketplaceListTest() {
        when(appPublicationEntityMapper.countPublished(any(), any())).thenReturn(0L);

        var page = publicAppApplicationService.listPublishedApps(
                new com.codeforge.ai.application.dto.publication.PublicAppQueryRequest(1L, 12L, null, null, "LATEST"));

        assertThat(page.records()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void ArchivedAppMarketplaceDetailReturnsNotFoundTest() {
        assertThatThrownBy(() -> appPublicationApplicationService.requirePublishedBySlug(SLUG))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_NOT_FOUND);
    }

    @Test
    void ArchivedAppCannotIssuePreviewTokenTest() {
        assertThatThrownBy(() -> publicAppApplicationService.issuePublicPreviewToken(SLUG))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_NOT_FOUND);
    }

    @Test
    void ArchivedAppCannotIssueDownloadTokenTest() {
        assertThatThrownBy(() -> publicAppApplicationService.issuePublicDownloadToken(SLUG))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_NOT_FOUND);
    }

    @Test
    void ArchivedAppOldDownloadTokenIsRejectedTest() throws Exception {
        assertThatThrownBy(() -> publicDownloadApplicationService.downloadByToken(downloadToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_NOT_FOUND);
    }

    @Test
    void ArchivedAppStaticPreviewIsRejectedTest() {
        ErrorCode error = previewAccessTokenService.resolvePreviewTokenError(
                previewToken, VERSION_ID, appPublicationEntityMapper);

        assertThat(error).isEqualTo(ErrorCode.PUBLICATION_NOT_FOUND);
    }

    @Test
    void UnarchivedPublishedAppStillWorksTest() throws Exception {
        when(aiAppEntityMapper.selectOneById(APP_ID)).thenReturn(AiAppEntity.builder()
                .id(APP_ID)
                .status(AiAppStatus.DEVELOPING.name())
                .build());

        AppPublicationEntity publication = publishedPublication();
        assertThat(appPublicationApplicationService.requirePublishedBySlug(SLUG).getId())
                .isEqualTo(publication.getId());

        Path zipPath = tempDir.resolve("active.zip");
        Files.write(zipPath, new byte[] {9, 8, 7});
        when(exportPackageEntityMapper.selectOneById(PACKAGE_ID)).thenReturn(
                ExportPackageEntity.builder()
                        .id(PACKAGE_ID)
                        .appId(APP_ID)
                        .appVersionId(VERSION_ID)
                        .status("READY")
                        .storagePath(zipPath.toString())
                        .build());

        ResponseEntity<Resource> response = publicDownloadApplicationService.downloadByToken(downloadToken);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private void stubArchivedPublishedState() throws Exception {
        when(appPublicationEntityMapper.findBySlug(SLUG)).thenReturn(publishedPublication());
        when(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).thenReturn(publishedPublication());
        when(aiAppEntityMapper.selectOneById(APP_ID)).thenReturn(AiAppEntity.builder()
                .id(APP_ID)
                .status(AiAppStatus.ARCHIVED.name())
                .build());
        Path zipPath = tempDir.resolve("archived.zip");
        Files.write(zipPath, new byte[] {1, 2, 3});
        when(exportPackageEntityMapper.selectOneById(PACKAGE_ID)).thenReturn(
                ExportPackageEntity.builder()
                        .id(PACKAGE_ID)
                        .appId(APP_ID)
                        .appVersionId(VERSION_ID)
                        .status("READY")
                        .storagePath(zipPath.toString())
                        .build());
    }

    private AppPublicationEntity publishedPublication() {
        return AppPublicationEntity.builder()
                .id(PUBLICATION_ID)
                .appId(APP_ID)
                .versionId(VERSION_ID)
                .status(AppPublicationStatus.PUBLISHED)
                .slug(SLUG)
                .allowPreview(true)
                .allowDownload(true)
                .build();
    }
}
