package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeforge.ai.application.dto.publication.PublicAppDetailResponse;
import com.codeforge.ai.application.dto.publication.PublicAppQueryRequest;
import com.codeforge.ai.application.dto.publication.PublicDownloadTokenResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.domain.app.enums.AppPublicationStatus;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.security.DownloadAccessTokenService;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.domain.app.enums.PublicationDownloadAvailability;
import com.codeforge.ai.infrastructure.persistence.projection.VersionExportStatusRow;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicAppApplicationServiceTest {

    @Mock
    private AppPublicationEntityMapper appPublicationEntityMapper;
    @Mock
    private AppPublicationApplicationService appPublicationApplicationService;
    @Mock
    private AiAppEntityMapper aiAppEntityMapper;
    @Mock
    private AppVersionEntityMapper appVersionEntityMapper;
    @Mock
    private UserEntityMapper userEntityMapper;
    @Mock
    private ExportPackageEntityMapper exportPackageEntityMapper;
    @Mock
    private PreviewAccessTokenService previewAccessTokenService;
    @Mock
    private DownloadAccessTokenService downloadAccessTokenService;

    @InjectMocks
    private PublicAppApplicationService publicAppApplicationService;

    private AppPublicationEntity publication;

    @BeforeEach
    void setUp() {
        publication = AppPublicationEntity.builder()
                .id(9001L)
                .appId(100L)
                .versionId(45L)
                .publisherUserId(7L)
                .publicTitle("客户管理后台")
                .publicDescription("公开简介")
                .slug("customer-management-a7k9m2")
                .status(AppPublicationStatus.PUBLISHED)
                .allowPreview(true)
                .allowDownload(false)
                .viewCount(3L)
                .downloadCount(1L)
                .build();
    }

    @Test
    void publicListOnlyPublishedTest() {
        when(appPublicationEntityMapper.countPublished(any(), any())).thenReturn(1L);
        when(appPublicationEntityMapper.findPublishedPage(any(), any(), anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(publication));
        when(aiAppEntityMapper.findByIds(List.of(100L))).thenReturn(List.of(
                AiAppEntity.builder().id(100L).appType("ADMIN_WEB").build()));
        when(appVersionEntityMapper.findByIds(List.of(45L))).thenReturn(List.of(
                AppVersionEntity.builder().id(45L).versionNo(2).build()));
        when(userEntityMapper.findByIds(List.of(7L))).thenReturn(List.of(
                UserEntity.builder().id(7L).displayName("测试用户").build()));
        VersionExportStatusRow exportRow = new VersionExportStatusRow();
        exportRow.setAppVersionId(45L);
        exportRow.setStatus("READY");
        when(exportPackageEntityMapper.findLatestStatusByVersionIds(List.of(45L))).thenReturn(List.of(exportRow));

        var page = publicAppApplicationService.listPublishedApps(new PublicAppQueryRequest(1L, 12L, null, null, "LATEST"));

        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).slug()).isEqualTo("customer-management-a7k9m2");
        assertThat(page.records().get(0).versionNo()).isEqualTo(2);
        assertThat(page.records().get(0).downloadAvailability()).isEqualTo(PublicationDownloadAvailability.DISABLED);
    }

    @Test
    void publicDetailTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("customer-management-a7k9m2"))
                .thenReturn(publication);
        when(aiAppEntityMapper.selectOneById(100L))
                .thenReturn(AiAppEntity.builder().id(100L).appType("ADMIN_WEB").build());
        when(appVersionEntityMapper.findByAppIdAndVersionId(100L, 45L))
                .thenReturn(AppVersionEntity.builder().id(45L).versionNo(2).versionSource("AI_DIRECT").build());
        when(userEntityMapper.findById(7L))
                .thenReturn(UserEntity.builder().id(7L).displayName("测试用户").build());
        VersionExportStatusRow exportRow = new VersionExportStatusRow();
        exportRow.setAppVersionId(45L);
        exportRow.setStatus("READY");
        when(exportPackageEntityMapper.findLatestStatusByVersionIds(List.of(45L))).thenReturn(List.of(exportRow));

        PublicAppDetailResponse detail = publicAppApplicationService.getPublishedAppDetail("customer-management-a7k9m2");

        assertThat(detail.publicTitle()).isEqualTo("客户管理后台");
        assertThat(detail.versionNo()).isEqualTo(2);
        assertThat(detail.generationSource()).isEqualTo("AI_DIRECT");
        assertThat(detail.downloadAvailability()).isEqualTo(PublicationDownloadAvailability.DISABLED);
    }

    @Test
    void publicPreviewPermissionTest() {
        publication.setAllowPreview(false);
        when(appPublicationApplicationService.requirePublishedBySlug("customer-management-a7k9m2"))
                .thenReturn(publication);

        assertThatThrownBy(() -> publicAppApplicationService.issuePublicPreviewToken("customer-management-a7k9m2"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_PREVIEW_DISABLED);
    }

    @Test
    void publicDownloadPermissionTest() {
        publication.setAllowDownload(false);
        when(appPublicationApplicationService.requirePublishedBySlug("customer-management-a7k9m2"))
                .thenReturn(publication);

        assertThatThrownBy(() -> publicAppApplicationService.issuePublicDownloadToken("customer-management-a7k9m2"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_DOWNLOAD_DISABLED);
    }

    @Test
    void publicDownloadRequiresReadyExportTest() {
        publication.setAllowDownload(true);
        when(appPublicationApplicationService.requirePublishedBySlug("customer-management-a7k9m2"))
                .thenReturn(publication);
        when(appVersionEntityMapper.findByAppIdAndVersionId(100L, 45L))
                .thenReturn(AppVersionEntity.builder().id(45L).appId(100L).versionNo(2).build());
        when(exportPackageEntityMapper.findLatestReadyByAppVersionId(45L)).thenReturn(null);

        assertThatThrownBy(() -> publicAppApplicationService.issuePublicDownloadToken("customer-management-a7k9m2"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PUBLICATION_EXPORT_NOT_READY);
    }

    @Test
    void publicationDownloadUsesFixedVersionTest() {
        publication.setAllowDownload(true);
        ExportPackageEntity exportPackage = ExportPackageEntity.builder().id(501L).status("READY").build();
        when(appPublicationApplicationService.requirePublishedBySlug("customer-management-a7k9m2"))
                .thenReturn(publication);
        when(appVersionEntityMapper.findByAppIdAndVersionId(100L, 45L))
                .thenReturn(AppVersionEntity.builder().id(45L).appId(100L).versionNo(2).build());
        when(exportPackageEntityMapper.findLatestReadyByAppVersionId(45L)).thenReturn(exportPackage);
        when(downloadAccessTokenService.createDownloadToken(9001L, 100L, 45L, 501L)).thenReturn("download-token");
        when(downloadAccessTokenService.getDownloadTokenExpireSeconds()).thenReturn(600L);

        publicAppApplicationService.issuePublicDownloadToken("customer-management-a7k9m2");

        verify(exportPackageEntityMapper).findLatestReadyByAppVersionId(45L);
        verify(downloadAccessTokenService).createDownloadToken(9001L, 100L, 45L, 501L);
    }

    @Test
    void downloadTokenBoundToPublicationVersionTest() {
        publication.setAllowDownload(true);
        ExportPackageEntity exportPackage = ExportPackageEntity.builder().id(501L).status("READY").build();
        when(appPublicationApplicationService.requirePublishedBySlug("customer-management-a7k9m2"))
                .thenReturn(publication);
        when(appVersionEntityMapper.findByAppIdAndVersionId(100L, 45L))
                .thenReturn(AppVersionEntity.builder().id(45L).appId(100L).versionNo(2).build());
        when(exportPackageEntityMapper.findLatestReadyByAppVersionId(45L)).thenReturn(exportPackage);
        when(downloadAccessTokenService.createDownloadToken(9001L, 100L, 45L, 501L)).thenReturn("download-token");
        when(downloadAccessTokenService.getDownloadTokenExpireSeconds()).thenReturn(600L);

        PublicDownloadTokenResponse response =
                publicAppApplicationService.issuePublicDownloadToken("customer-management-a7k9m2");

        assertThat(response.downloadUrl()).contains("download-token");
        verify(downloadAccessTokenService).createDownloadToken(9001L, 100L, 45L, 501L);
    }

    @Test
    void PublishedPublicationCanReadPinnedVersionTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("customer-management-a7k9m2"))
                .thenReturn(publication);
        when(previewAccessTokenService.createPublicPreviewToken(9001L, 100L, 45L)).thenReturn("preview-token");
        when(previewAccessTokenService.getPreviewTokenExpireSeconds()).thenReturn(600L);

        var response = publicAppApplicationService.issuePublicPreviewToken("customer-management-a7k9m2");

        assertThat(response.previewUrl()).contains("preview-token");
        verify(previewAccessTokenService).createPublicPreviewToken(9001L, 100L, 45L);
    }

    @Test
    void PublicCannotReadUnpinnedVersionTest() {
        publication.setVersionId(45L);
        when(appPublicationApplicationService.requirePublishedBySlug("customer-management-a7k9m2"))
                .thenReturn(publication);
        when(previewAccessTokenService.createPublicPreviewToken(9001L, 100L, 45L)).thenReturn("preview-token");
        when(previewAccessTokenService.getPreviewTokenExpireSeconds()).thenReturn(600L);

        publicAppApplicationService.issuePublicPreviewToken("customer-management-a7k9m2");

        verify(previewAccessTokenService).createPublicPreviewToken(9001L, 100L, 45L);
        verify(appVersionEntityMapper, never()).findByAppIdAndVersionId(100L, 99L);
    }

    @Test
    void ArchivedPublicationIsNotPublicTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("archived-app"))
                .thenThrow(new BusinessException(ErrorCode.PUBLICATION_NOT_FOUND));

        assertThatThrownBy(() -> publicAppApplicationService.getPublishedAppDetail("archived-app"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void UnpublishedApplicationIsNotPublicTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("draft-only"))
                .thenThrow(new BusinessException(ErrorCode.PUBLICATION_NOT_FOUND));

        assertThatThrownBy(() -> publicAppApplicationService.getPublishedAppDetail("draft-only"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unpublishedPreviewDeniedTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("missing"))
                .thenThrow(new BusinessException(ErrorCode.PUBLICATION_NOT_FOUND));

        assertThatThrownBy(() -> publicAppApplicationService.issuePublicPreviewToken("missing"))
                .isInstanceOf(BusinessException.class);
        verify(previewAccessTokenService, never()).createPublicPreviewToken(anyLong(), anyLong(), anyLong());
    }
}
