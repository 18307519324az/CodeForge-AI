package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.publication.AppPublicationCreateRequest;
import com.codeforge.ai.application.dto.publication.AppPublicationResponse;
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
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AppPublicationApplicationServiceTest {

    private static final Long APP_ID = 3001L;
    private static final Long WORKSPACE_ID = 1001L;
    private static final Long CURRENT_VERSION_ID = 7001L;
    private static final Long PUBLISH_VERSION_ID = 7002L;
    private static final Long USER_ID = 2001L;
    private static final Long PUBLICATION_ID = 9101L;

    private AiAppEntityMapper aiAppEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private AppPublicationEntityMapper appPublicationEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private ExportPackageEntityMapper exportPackageEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private VueProjectBuildService vueProjectBuildService;
    private AppPublicationApplicationService appPublicationApplicationService;

    private final CurrentUser editorUser = new CurrentUser(USER_ID, "editor", List.of("USER"));

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        appPublicationEntityMapper = mock(AppPublicationEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        vueProjectBuildService = mock(VueProjectBuildService.class);
        MarketplacePublicationAccessGuard marketplacePublicationAccessGuard =
                new MarketplacePublicationAccessGuard(aiAppEntityMapper);
        MarketplaceAuditService marketplaceAuditService = mock(MarketplaceAuditService.class);
        appPublicationApplicationService = new AppPublicationApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                appPublicationEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                workspaceAccessService,
                vueProjectBuildService,
                marketplacePublicationAccessGuard,
                marketplaceAuditService
        );
    }

    @Test
    void publishAppVersionTest() {
        stubPublishableApp();
        stubPublishableVersion(PUBLISH_VERSION_ID, 2);
        stubPublishableArtifacts(PUBLISH_VERSION_ID);
        given(appPublicationEntityMapper.findByAppId(APP_ID)).willReturn(null);
        given(appPublicationEntityMapper.findBySlug(any())).willReturn(null);
        doAnswer(invocation -> {
            AppPublicationEntity entity = invocation.getArgument(0);
            entity.setId(PUBLICATION_ID);
            return 1;
        }).when(appPublicationEntityMapper).insert(any(AppPublicationEntity.class));

        AppPublicationCreateRequest request = new AppPublicationCreateRequest(
                PUBLISH_VERSION_ID,
                "Customer Portal",
                "Public demo",
                true,
                false
        );

        AppPublicationResponse response = appPublicationApplicationService.publishApp(editorUser, APP_ID, request);

        ArgumentCaptor<AppPublicationEntity> captor = ArgumentCaptor.forClass(AppPublicationEntity.class);
        verify(appPublicationEntityMapper).insert(captor.capture());
        AppPublicationEntity saved = captor.getValue();

        assertThat(saved.getVersionId()).isEqualTo(PUBLISH_VERSION_ID);
        assertThat(saved.getStatus()).isEqualTo(AppPublicationStatus.PUBLISHED);
        assertThat(saved.getPublicTitle()).isEqualTo("Customer Portal");
        assertThat(saved.getAllowPreview()).isTrue();
        assertThat(saved.getAllowDownload()).isFalse();
        assertThat(saved.getPublisherUserId()).isEqualTo(USER_ID);
        assertThat(saved.getSlug()).matches("customer-portal-[a-z0-9]{6}");

        assertThat(response.publicationId()).isEqualTo(PUBLICATION_ID);
        assertThat(response.appId()).isEqualTo(APP_ID);
        assertThat(response.versionId()).isEqualTo(PUBLISH_VERSION_ID);
        assertThat(response.versionNo()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(AppPublicationStatus.PUBLISHED);
        verify(workspaceAccessService).requireEditorAccess(editorUser, WORKSPACE_ID);
    }

    @Test
    void publishVersionOwnershipTest() {
        stubPublishableApp();
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, PUBLISH_VERSION_ID))
                .willReturn(AppVersionEntity.builder()
                        .id(PUBLISH_VERSION_ID)
                        .appId(9999L)
                        .versionNo(2)
                        .build());

        AppPublicationCreateRequest request = new AppPublicationCreateRequest(
                PUBLISH_VERSION_ID,
                "Customer Portal",
                null,
                true,
                false
        );

        assertThatThrownBy(() -> appPublicationApplicationService.publishApp(editorUser, APP_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.PUBLICATION_VERSION_NOT_OWNED);
                });
    }

    @Test
    void publishPermissionTest() {
        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .status("ACTIVE")
                .build());
        doThrow(new BusinessException(ErrorCode.RESOURCE_FORBIDDEN))
                .when(workspaceAccessService).requireEditorAccess(editorUser, WORKSPACE_ID);

        AppPublicationCreateRequest request = new AppPublicationCreateRequest(
                PUBLISH_VERSION_ID,
                "Customer Portal",
                null,
                true,
                false
        );

        assertThatThrownBy(() -> appPublicationApplicationService.publishApp(editorUser, APP_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
                });
        verify(workspaceAccessService).requireEditorAccess(editorUser, WORKSPACE_ID);
    }

    @Test
    void publicationFixedVersionTest() {
        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .status("ACTIVE")
                .currentVersionId(CURRENT_VERSION_ID)
                .build());
        stubPublishableVersion(PUBLISH_VERSION_ID, 2);
        stubPublishableArtifacts(PUBLISH_VERSION_ID);
        given(appPublicationEntityMapper.findByAppId(APP_ID)).willReturn(null);
        given(appPublicationEntityMapper.findBySlug(any())).willReturn(null);
        doAnswer(invocation -> {
            AppPublicationEntity entity = invocation.getArgument(0);
            entity.setId(PUBLICATION_ID);
            return 1;
        }).when(appPublicationEntityMapper).insert(any(AppPublicationEntity.class));

        AppPublicationCreateRequest request = new AppPublicationCreateRequest(
                PUBLISH_VERSION_ID,
                "Pinned Version Release",
                null,
                true,
                false
        );

        AppPublicationResponse response = appPublicationApplicationService.publishApp(editorUser, APP_ID, request);

        ArgumentCaptor<AppPublicationEntity> captor = ArgumentCaptor.forClass(AppPublicationEntity.class);
        verify(appPublicationEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getVersionId()).isEqualTo(PUBLISH_VERSION_ID);
        assertThat(captor.getValue().getVersionId()).isNotEqualTo(CURRENT_VERSION_ID);
        assertThat(response.versionId()).isEqualTo(PUBLISH_VERSION_ID);
        assertThat(response.versionNo()).isEqualTo(2);
    }

    @Test
    void unpublishTest() {
        stubPublishableApp();
        AppPublicationEntity publication = AppPublicationEntity.builder()
                .id(PUBLICATION_ID)
                .appId(APP_ID)
                .versionId(PUBLISH_VERSION_ID)
                .publisherUserId(USER_ID)
                .publicTitle("Customer Portal")
                .slug("customer-portal-abc123")
                .status(AppPublicationStatus.PUBLISHED)
                .allowPreview(true)
                .allowDownload(false)
                .publishedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .viewCount(12L)
                .downloadCount(3L)
                .build();
        given(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).willReturn(publication);
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, PUBLISH_VERSION_ID))
                .willReturn(AppVersionEntity.builder()
                        .id(PUBLISH_VERSION_ID)
                        .appId(APP_ID)
                        .versionNo(2)
                        .build());

        AppPublicationResponse response = appPublicationApplicationService.unpublishApp(
                editorUser, APP_ID, PUBLICATION_ID);

        ArgumentCaptor<AppPublicationEntity> captor = ArgumentCaptor.forClass(AppPublicationEntity.class);
        verify(appPublicationEntityMapper).update(captor.capture());
        AppPublicationEntity updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(AppPublicationStatus.UNPUBLISHED);
        assertThat(updated.getUnpublishedAt()).isNotNull();
        assertThat(updated.getUpdatedBy()).isEqualTo(USER_ID);

        assertThat(response.publicationId()).isEqualTo(PUBLICATION_ID);
        assertThat(response.status()).isEqualTo(AppPublicationStatus.UNPUBLISHED);
        assertThat(response.unpublishedAt()).isNotNull();
    }

    @Test
    void publishWithAllowDownloadRequiresReadyExport() {
        stubPublishableApp();
        stubPublishableVersion(PUBLISH_VERSION_ID, 2);
        stubPublishableArtifacts(PUBLISH_VERSION_ID);
        given(exportPackageEntityMapper.findLatestReadyByAppVersionId(PUBLISH_VERSION_ID)).willReturn(null);

        AppPublicationCreateRequest request = new AppPublicationCreateRequest(
                PUBLISH_VERSION_ID,
                "Customer Portal",
                null,
                true,
                true
        );

        assertThatThrownBy(() -> appPublicationApplicationService.publishApp(editorUser, APP_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.PUBLICATION_EXPORT_NOT_READY);
                });
    }

    private void stubPublishableApp() {
        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .status("ACTIVE")
                .currentVersionId(CURRENT_VERSION_ID)
                .build());
    }

    private void stubPublishableVersion(Long versionId, int versionNo) {
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, versionId))
                .willReturn(AppVersionEntity.builder()
                        .id(versionId)
                        .appId(APP_ID)
                        .versionNo(versionNo)
                        .build());
    }

    private void stubPublishableArtifacts(Long versionId) {
        given(generatedFileEntityMapper.findByAppVersionId(versionId)).willReturn(List.of(
                GeneratedFileEntity.builder()
                        .appVersionId(versionId)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileType("html")
                        .build()
        ));
        given(vueProjectBuildService.serveBuiltFile(eq(versionId), eq("index.html")))
                .willReturn(Optional.empty());
        given(exportPackageEntityMapper.findLatestReadyByAppVersionId(versionId))
                .willReturn(ExportPackageEntity.builder()
                        .appVersionId(versionId)
                        .status("READY")
                        .build());
    }
}
