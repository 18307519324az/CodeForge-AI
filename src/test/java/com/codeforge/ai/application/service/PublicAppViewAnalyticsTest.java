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

import com.codeforge.ai.application.dto.publication.PublicAppQueryRequest;
import com.codeforge.ai.application.dto.publication.PublicAppViewResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.enums.AppPublicationStatus;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PublicationViewDedupeEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.projection.VersionExportStatusRow;
import com.codeforge.ai.infrastructure.security.DownloadAccessTokenService;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.infrastructure.security.PublicationViewerIdentityService;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicAppViewAnalyticsTest {

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
    @Mock
    private PublicationViewDedupeEntityMapper publicationViewDedupeEntityMapper;
    @Mock
    private PublicationViewerIdentityService publicationViewerIdentityService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

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
                .slug("app-n41xup")
                .status(AppPublicationStatus.PUBLISHED)
                .allowPreview(true)
                .allowDownload(false)
                .viewCount(23L)
                .build();
    }

    @Test
    void viewCountGetDetailDoesNotIncrementTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("app-n41xup")).thenReturn(publication);
        stubDetailLookups();

        for (int i = 0; i < 10; i++) {
            publicAppApplicationService.getPublishedAppDetail("app-n41xup");
        }

        verify(appPublicationEntityMapper, never()).incrementViewCount(anyLong());
    }

    @Test
    void viewCountFirstDetailIncrementsTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("app-n41xup")).thenReturn(publication);
        when(publicationViewerIdentityService.resolveViewerKey(null, request, response)).thenReturn("a:viewer-1");
        when(publicationViewerIdentityService.hashViewerKey("a:viewer-1")).thenReturn("hash-1");
        when(publicationViewDedupeEntityMapper.insertIgnore(eq(9001L), eq("hash-1"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(appPublicationEntityMapper.findActiveById(9001L)).thenReturn(
                AppPublicationEntity.builder().id(9001L).viewCount(24L).build());

        PublicAppViewResponse result =
                publicAppApplicationService.recordPublicAppView("app-n41xup", null, request, response);

        assertThat(result.counted()).isTrue();
        assertThat(result.viewCount()).isEqualTo(24L);
        verify(appPublicationEntityMapper).incrementViewCount(9001L);
    }

    @Test
    void viewCountSameViewerWithin24hDoesNotIncrementTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("app-n41xup")).thenReturn(publication);
        when(publicationViewerIdentityService.resolveViewerKey(null, request, response)).thenReturn("a:viewer-1");
        when(publicationViewerIdentityService.hashViewerKey("a:viewer-1")).thenReturn("hash-1");
        when(publicationViewDedupeEntityMapper.insertIgnore(eq(9001L), eq("hash-1"), any(LocalDateTime.class)))
                .thenReturn(0);
        when(publicationViewDedupeEntityMapper.updateIfExpired(
                eq(9001L), eq("hash-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);
        when(appPublicationEntityMapper.findActiveById(9001L)).thenReturn(publication);

        PublicAppViewResponse result =
                publicAppApplicationService.recordPublicAppView("app-n41xup", null, request, response);

        assertThat(result.counted()).isFalse();
        assertThat(result.viewCount()).isEqualTo(23L);
        verify(appPublicationEntityMapper, never()).incrementViewCount(anyLong());
    }

    @Test
    void viewCountDifferentViewerIncrementsTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("app-n41xup")).thenReturn(publication);
        when(publicationViewerIdentityService.resolveViewerKey(7L, request, response)).thenReturn("u:7");
        when(publicationViewerIdentityService.hashViewerKey("u:7")).thenReturn("hash-user");
        when(publicationViewDedupeEntityMapper.insertIgnore(eq(9001L), eq("hash-user"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(appPublicationEntityMapper.findActiveById(9001L)).thenReturn(
                AppPublicationEntity.builder().id(9001L).viewCount(24L).build());

        PublicAppViewResponse result =
                publicAppApplicationService.recordPublicAppView("app-n41xup", 7L, request, response);

        assertThat(result.counted()).isTrue();
        verify(appPublicationEntityMapper).incrementViewCount(9001L);
    }

    @Test
    void viewCountAfter24hIncrementsAgainTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("app-n41xup")).thenReturn(publication);
        when(publicationViewerIdentityService.resolveViewerKey(null, request, response)).thenReturn("a:viewer-1");
        when(publicationViewerIdentityService.hashViewerKey("a:viewer-1")).thenReturn("hash-1");
        when(publicationViewDedupeEntityMapper.insertIgnore(eq(9001L), eq("hash-1"), any(LocalDateTime.class)))
                .thenReturn(0);
        when(publicationViewDedupeEntityMapper.updateIfExpired(
                eq(9001L), eq("hash-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(appPublicationEntityMapper.findActiveById(9001L)).thenReturn(
                AppPublicationEntity.builder().id(9001L).viewCount(24L).build());

        PublicAppViewResponse result =
                publicAppApplicationService.recordPublicAppView("app-n41xup", null, request, response);

        assertThat(result.counted()).isTrue();
        verify(appPublicationEntityMapper).incrementViewCount(9001L);
    }

    @Test
    void unpublishedAppCannotRecordViewTest() {
        when(appPublicationApplicationService.requirePublishedBySlug("missing"))
                .thenThrow(new BusinessException(ErrorCode.PUBLICATION_NOT_FOUND));

        assertThatThrownBy(() -> publicAppApplicationService.recordPublicAppView("missing", null, request, response))
                .isInstanceOf(BusinessException.class);
        verify(publicationViewDedupeEntityMapper, never()).insertIgnore(anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void viewCountListDoesNotIncrementTest() {
        when(appPublicationEntityMapper.countPublished(any(), any())).thenReturn(0L);

        publicAppApplicationService.listPublishedApps(new PublicAppQueryRequest(1L, 12L, null, null, "LATEST"));

        verify(appPublicationEntityMapper, never()).incrementViewCount(anyLong());
    }

    private void stubDetailLookups() {
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
    }
}
