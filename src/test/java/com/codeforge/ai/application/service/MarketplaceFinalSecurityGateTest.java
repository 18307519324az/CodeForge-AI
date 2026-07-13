package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeforge.ai.application.dto.publication.AppPublicationUpdateRequest;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.app.enums.AppPublicationStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.DownloadAccessTokenService;
import com.codeforge.ai.infrastructure.security.DownloadGrantRedemptionService;
import com.codeforge.ai.infrastructure.security.JwtProperties;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.util.DownloadResponseSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

class MarketplaceFinalSecurityGateTest {

    private static final Long OWNER_USER_ID = 8L;
    private static final Long FOREIGN_USER_ID = 9L;
    private static final Long APP_ID = 3001L;
    private static final Long WORKSPACE_ID = 1001L;
    private static final Long VERSION_V1_ID = 7001L;
    private static final Long VERSION_V2_ID = 7002L;
    private static final Long PACKAGE_P1_ID = 9001L;
    private static final Long PACKAGE_FOREIGN_ID = 9002L;
    private static final Long PUBLICATION_ID = 9101L;

    private final CurrentUser ownerUser = new CurrentUser(OWNER_USER_ID, "amns", List.of("USER"));
    private final CurrentUser foreignUser = new CurrentUser(FOREIGN_USER_ID, "p0userb", List.of("USER"));

    @TempDir
    Path tempDir;

    private AiAppEntityMapper aiAppEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private ExportPackageEntityMapper exportPackageEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private VueProjectBuildService vueProjectBuildService;
    private ExportPackageApplicationService exportPackageApplicationService;

    private AppPublicationEntityMapper appPublicationEntityMapper;
    private DownloadAccessTokenService downloadAccessTokenService;
    private PublicDownloadApplicationService publicDownloadApplicationService;
    private AppPublicationApplicationService appPublicationApplicationService;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        vueProjectBuildService = mock(VueProjectBuildService.class);
        exportPackageApplicationService = new ExportPackageApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                workspaceAccessService);
        ReflectionTestUtils.setField(exportPackageApplicationService, "exportRoot", tempDir.toString());

        appPublicationEntityMapper = mock(AppPublicationEntityMapper.class);
        MarketplacePublicationAccessGuard marketplacePublicationAccessGuard =
                new MarketplacePublicationAccessGuard(aiAppEntityMapper);
        downloadAccessTokenService = new DownloadAccessTokenService(
                new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L),
                marketplacePublicationAccessGuard);
        publicDownloadApplicationService = new PublicDownloadApplicationService(
                downloadAccessTokenService,
                new DownloadGrantRedemptionService(),
                appPublicationEntityMapper,
                exportPackageEntityMapper);

        appPublicationApplicationService = new AppPublicationApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                appPublicationEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                workspaceAccessService,
                vueProjectBuildService,
                marketplacePublicationAccessGuard,
                new MarketplaceAuditService(mock(com.codeforge.ai.infrastructure.audit.AuditLogWriter.class), new ObjectMapper()));
        stubActivePublishedApp();
    }

    private void stubActivePublishedApp() {
        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .status("ACTIVE")
                .build());
    }

    @Test
    void OwnerCanDownloadExistingPrivatePackageTest() throws Exception {
        Path zipPath = createReadyPackage(PACKAGE_P1_ID, VERSION_V1_ID, "owner-private.zip");
        stubOwnerReadableApp();

        Path resolved = exportPackageApplicationService.getPackagePath(ownerUser, PACKAGE_P1_ID);

        assertThat(resolved).isEqualTo(zipPath);
        assertThat(Files.readAllBytes(resolved)).isNotEmpty();
    }

    @Test
    void ForeignUserCannotDownloadExistingPrivatePackageTest() {
        createReadyPackage(PACKAGE_P1_ID, VERSION_V1_ID, "owner-private.zip");
        stubOwnerReadableApp();
        doThrow(new BusinessException(ErrorCode.RESOURCE_FORBIDDEN))
                .when(workspaceAccessService).requireReadAccess(foreignUser, WORKSPACE_ID);

        assertThatThrownBy(() -> exportPackageApplicationService.getPackagePath(foreignUser, PACKAGE_P1_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    @Test
    void AnonymousCannotDownloadPrivatePackageTest() {
        createReadyPackage(PACKAGE_P1_ID, VERSION_V1_ID, "owner-private.zip");
        stubOwnerReadableApp();

        assertThatThrownBy(() -> exportPackageApplicationService.getPackagePath(null, PACKAGE_P1_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void MarketplacePublishedVersionIsPinnedTest() {
        AppPublicationEntity publication = publishedPublication(VERSION_V1_ID);

        assertThat(publication.getVersionId()).isEqualTo(VERSION_V1_ID);
        assertThat(publication.getStatus()).isEqualTo(AppPublicationStatus.PUBLISHED);
    }

    @Test
    void NewAppVersionDoesNotChangePublishedVersionTest() {
        AppPublicationEntity publication = publishedPublication(VERSION_V1_ID);
        given(appPublicationEntityMapper.findByAppId(APP_ID)).willReturn(publication);
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, VERSION_V2_ID))
                .willReturn(AppVersionEntity.builder()
                        .id(VERSION_V2_ID)
                        .appId(APP_ID)
                        .versionNo(2)
                        .build());

        AppPublicationEntity unchanged = appPublicationEntityMapper.findByAppId(APP_ID);

        assertThat(unchanged.getVersionId()).isEqualTo(VERSION_V1_ID);
        assertThat(unchanged.getVersionId()).isNotEqualTo(VERSION_V2_ID);
    }

    @Test
    void ExplicitRepublishChangesPinnedVersionTest() {
        stubPublishableApp();
        stubVersion(VERSION_V1_ID, 1);
        stubVersion(VERSION_V2_ID, 2);
        stubPublishableArtifacts(VERSION_V2_ID);
        AppPublicationEntity publication = publishedPublication(VERSION_V1_ID);
        given(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).willReturn(publication);

        AppPublicationUpdateRequest request = new AppPublicationUpdateRequest(
                VERSION_V2_ID, null, null, null, null);
        appPublicationApplicationService.updatePublication(ownerUser, APP_ID, PUBLICATION_ID, request);

        assertThat(publication.getVersionId()).isEqualTo(VERSION_V2_ID);
    }

    @Test
    void ArchivedMarketplaceCannotBeViewedTest() {
        AppPublicationEntity unpublished = AppPublicationEntity.builder()
                .id(PUBLICATION_ID)
                .appId(APP_ID)
                .versionId(VERSION_V1_ID)
                .status(AppPublicationStatus.UNPUBLISHED)
                .build();
        given(appPublicationEntityMapper.findBySlug("archived-slug")).willReturn(unpublished);

        assertThatThrownBy(() -> appPublicationApplicationService.requirePublishedBySlug("archived-slug"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_NOT_FOUND);
    }

    @Test
    void ArchivedMarketplaceCannotBeDownloadedTest() throws Exception {
        Path zipPath = createReadyPackage(PACKAGE_P1_ID, VERSION_V1_ID, "published.zip");
        String token = downloadAccessTokenService.createDownloadToken(
                PUBLICATION_ID, APP_ID, VERSION_V1_ID, PACKAGE_P1_ID);
        when(exportPackageEntityMapper.selectOneById(PACKAGE_P1_ID)).thenReturn(
                ExportPackageEntity.builder()
                        .id(PACKAGE_P1_ID)
                        .appId(APP_ID)
                        .appVersionId(VERSION_V1_ID)
                        .status("READY")
                        .storagePath(zipPath.toString())
                        .build());
        when(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).thenReturn(
                AppPublicationEntity.builder()
                        .id(PUBLICATION_ID)
                        .appId(APP_ID)
                        .versionId(VERSION_V1_ID)
                        .status(AppPublicationStatus.UNPUBLISHED)
                        .allowDownload(true)
                        .build());

        assertThatThrownBy(() -> publicDownloadApplicationService.downloadByToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_NOT_PUBLISHED);
    }

    @Test
    void PackageMustBelongToPublishedVersionTest() throws Exception {
        Path zipPath = createReadyPackage(PACKAGE_FOREIGN_ID, VERSION_V2_ID, "foreign-version.zip");
        String token = downloadAccessTokenService.createDownloadToken(
                PUBLICATION_ID, APP_ID, VERSION_V1_ID, PACKAGE_FOREIGN_ID);
        when(exportPackageEntityMapper.selectOneById(PACKAGE_FOREIGN_ID)).thenReturn(
                ExportPackageEntity.builder()
                        .id(PACKAGE_FOREIGN_ID)
                        .appId(APP_ID)
                        .appVersionId(VERSION_V2_ID)
                        .status("READY")
                        .storagePath(zipPath.toString())
                        .build());
        when(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).thenReturn(
                publishedPublication(VERSION_V1_ID));

        assertThatThrownBy(() -> publicDownloadApplicationService.downloadByToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    @Test
    void VersionMustBelongToPublishedAppTest() {
        when(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).thenReturn(
                publishedPublication(VERSION_V1_ID));

        ErrorCode error = downloadAccessTokenService.resolveDownloadTokenError(
                downloadAccessTokenService.createDownloadToken(PUBLICATION_ID, 9999L, VERSION_V1_ID, PACKAGE_P1_ID),
                appPublicationEntityMapper);

        assertThat(error).isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
    }

    @Test
    void DirectPackageIdTamperingIsRejectedTest() {
        createReadyPackage(PACKAGE_P1_ID, VERSION_V1_ID, "owner-private.zip");
        stubOwnerReadableApp();
        doThrow(new BusinessException(ErrorCode.RESOURCE_FORBIDDEN))
                .when(workspaceAccessService).requireReadAccess(foreignUser, WORKSPACE_ID);

        assertThatThrownBy(() -> exportPackageApplicationService.getPackagePath(foreignUser, PACKAGE_P1_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
        verify(workspaceAccessService).requireReadAccess(foreignUser, WORKSPACE_ID);
    }

    @Test
    void DownloadFilenameCannotInjectHeadersTest() {
        String sanitized = DownloadResponseSupport.sanitizeFilename("evil\r\nX-Injected: yes.zip");
        assertThat(sanitized).doesNotContain("\r").doesNotContain("\n").doesNotContain(":");
        assertThat(DownloadResponseSupport.contentDispositionAttachment("evil\r\nX-Injected: yes.zip"))
                .isEqualTo("attachment; filename=\"yes.zip\"");
    }

    @Test
    void DownloadDoesNotExposeStoragePathTest() throws Exception {
        Path zipPath = tempDir.resolve("apps").resolve("3001").resolve("secret-internal.zip");
        Files.createDirectories(zipPath.getParent());
        Files.write(zipPath, "zip-content".getBytes(StandardCharsets.UTF_8));
        String token = downloadAccessTokenService.createDownloadToken(
                PUBLICATION_ID, APP_ID, VERSION_V1_ID, PACKAGE_P1_ID);
        when(exportPackageEntityMapper.selectOneById(PACKAGE_P1_ID)).thenReturn(
                ExportPackageEntity.builder()
                        .id(PACKAGE_P1_ID)
                        .appId(APP_ID)
                        .appVersionId(VERSION_V1_ID)
                        .status("READY")
                        .storagePath(zipPath.toString())
                        .build());
        when(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).thenReturn(
                publishedPublication(VERSION_V1_ID));

        ResponseEntity<Resource> response = publicDownloadApplicationService.downloadByToken(token);
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(disposition).contains("secret-internal.zip");
        assertThat(disposition).doesNotContain("apps\\3001");
        assertThat(disposition).doesNotContain("apps/3001");
        assertThat(disposition).doesNotContain(tempDir.toString());
    }

    @Test
    void OwnerPrivateArtifactSurvivesMarketplaceArchiveTest() throws Exception {
        Path zipPath = createReadyPackage(PACKAGE_P1_ID, VERSION_V1_ID, "owner-private.zip");
        stubOwnerReadableApp();
        AppPublicationEntity publication = publishedPublication(VERSION_V1_ID);
        publication.setStatus(AppPublicationStatus.UNPUBLISHED);
        given(appPublicationEntityMapper.findByAppId(APP_ID)).willReturn(publication);

        Path ownerPath = exportPackageApplicationService.getPackagePath(ownerUser, PACKAGE_P1_ID);

        assertThat(publication.getStatus()).isEqualTo(AppPublicationStatus.UNPUBLISHED);
        assertThat(ownerPath).isEqualTo(zipPath);
        assertThat(Files.readAllBytes(ownerPath)).isNotEmpty();
    }

    private Path createReadyPackage(Long packageId, Long versionId, String fileName) {
        try {
            Path zipPath = tempDir.resolve(fileName);
            Files.write(zipPath, "package-content".getBytes(StandardCharsets.UTF_8));
            ExportPackageEntity entity = ExportPackageEntity.builder()
                    .id(packageId)
                    .appId(APP_ID)
                    .appVersionId(versionId)
                    .status("READY")
                    .storagePath(zipPath.toString())
                    .build();
            given(exportPackageEntityMapper.selectOneById(packageId)).willReturn(entity);
            return zipPath;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void stubOwnerReadableApp() {
        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .status("ACTIVE")
                .visibility("PRIVATE")
                .build());
    }

    private void stubPublishableApp() {
        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .status("ACTIVE")
                .currentVersionId(VERSION_V1_ID)
                .build());
    }

    private void stubVersion(Long versionId, int versionNo) {
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, versionId))
                .willReturn(AppVersionEntity.builder()
                        .id(versionId)
                        .appId(APP_ID)
                        .versionNo(versionNo)
                        .build());
    }

    private AppPublicationEntity publishedPublication(Long versionId) {
        return AppPublicationEntity.builder()
                .id(PUBLICATION_ID)
                .appId(APP_ID)
                .versionId(versionId)
                .publisherUserId(OWNER_USER_ID)
                .publicTitle("Private App Publication")
                .slug("private-app-slug")
                .status(AppPublicationStatus.PUBLISHED)
                .allowPreview(true)
                .allowDownload(true)
                .build();
    }

    private void stubPublishableArtifacts(Long versionId) {
        given(generatedFileEntityMapper.findByAppVersionId(versionId)).willReturn(List.of(
                GeneratedFileEntity.builder()
                        .appVersionId(versionId)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileType("html")
                        .build()));
        given(vueProjectBuildService.serveBuiltFile(versionId, "index.html")).willReturn(Optional.empty());
        given(exportPackageEntityMapper.findLatestReadyByAppVersionId(versionId))
                .willReturn(ExportPackageEntity.builder()
                        .appVersionId(versionId)
                        .status("READY")
                        .build());
    }
}
