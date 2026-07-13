package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeforge.ai.application.dto.publication.AppPublicationCreateRequest;
import com.codeforge.ai.application.dto.publication.AppPublicationUpdateRequest;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.app.enums.AppPublicationStatus;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.AuditLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketplaceAuditTest {

    private static final Long APP_ID = 3001L;
    private static final Long WORKSPACE_ID = 1001L;
    private static final Long VERSION_V1 = 7001L;
    private static final Long VERSION_V2 = 7002L;
    private static final Long PUBLICATION_ID = 9101L;
    private static final Long USER_ID = 8L;

    private AuditLogEntityMapper auditLogEntityMapper;
    private AuditLogWriter auditLogWriter;
    private AppPublicationEntityMapper appPublicationEntityMapper;
    private AiAppEntityMapper aiAppEntityMapper;
    private AppPublicationApplicationService appPublicationApplicationService;
    private AiAppApplicationService aiAppApplicationService;
    private final CurrentUser owner = new CurrentUser(USER_ID, "amns", List.of("USER"));

    @BeforeEach
    void setUp() {
        AiAppEntityMapper aiAppEntityMapper = mock(AiAppEntityMapper.class);
        this.aiAppEntityMapper = aiAppEntityMapper;
        AppVersionEntityMapper appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        appPublicationEntityMapper = mock(AppPublicationEntityMapper.class);
        GeneratedFileEntityMapper generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        ExportPackageEntityMapper exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);
        auditLogEntityMapper = mock(AuditLogEntityMapper.class);
        auditLogWriter = new AuditLogWriter(auditLogEntityMapper);

        MarketplacePublicationAccessGuard guard = new MarketplacePublicationAccessGuard(aiAppEntityMapper);
        MarketplaceAuditService marketplaceAuditService = new MarketplaceAuditService(auditLogWriter, new ObjectMapper());

        appPublicationApplicationService = new AppPublicationApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                appPublicationEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                workspaceAccessService,
                mock(VueProjectBuildService.class),
                guard,
                marketplaceAuditService);

        aiAppApplicationService = new AiAppApplicationService(
                aiAppEntityMapper,
                workspaceAccessService,
                mock(AppListSummaryAggregator.class),
                appPublicationApplicationService);

        org.mockito.BDDMockito.given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .status("ACTIVE")
                .build());
        org.mockito.BDDMockito.given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, VERSION_V1))
                .willReturn(version(VERSION_V1, 1));
        org.mockito.BDDMockito.given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, VERSION_V2))
                .willReturn(version(VERSION_V2, 2));
        org.mockito.BDDMockito.given(generatedFileEntityMapper.findByAppVersionId(org.mockito.ArgumentMatchers.anyLong()))
                .willReturn(List.of(GeneratedFileEntity.builder()
                        .appVersionId(VERSION_V1)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileType("html")
                        .build()));
        org.mockito.BDDMockito.given(exportPackageEntityMapper.findLatestReadyByAppVersionId(org.mockito.ArgumentMatchers.anyLong()))
                .willReturn(ExportPackageEntity.builder().status("READY").build());
        org.mockito.BDDMockito.given(appPublicationEntityMapper.findBySlug(org.mockito.ArgumentMatchers.any()))
                .willReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            AppPublicationEntity entity = invocation.getArgument(0);
            entity.setId(PUBLICATION_ID);
            return 1;
        }).when(appPublicationEntityMapper).insert(any(AppPublicationEntity.class));
    }

    @Test
    void PublishWritesAuditCreatedAtTest() {
        org.mockito.BDDMockito.given(appPublicationEntityMapper.findByAppId(APP_ID)).willReturn(null);

        appPublicationApplicationService.publishApp(owner, APP_ID, new AppPublicationCreateRequest(
                VERSION_V1, "Gate App", "desc", true, false));

        AuditLogEntity audit = captureAudit();
        assertThat(audit.getActionCode()).isEqualTo("MARKETPLACE_PUBLISH");
        assertThat(audit.getCreatedAt()).isNotNull();
        assertThat(audit.getActorUserId()).isEqualTo(USER_ID);
    }

    @Test
    void RepublishWritesPinnedVersionAuditTest() {
        AppPublicationEntity publication = publication(VERSION_V1);
        org.mockito.BDDMockito.given(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).willReturn(publication);

        appPublicationApplicationService.updatePublication(owner, APP_ID, PUBLICATION_ID,
                new AppPublicationUpdateRequest(VERSION_V2, null, null, null, null));

        AuditLogEntity audit = captureAudit();
        assertThat(audit.getActionCode()).isEqualTo("MARKETPLACE_REPUBLISH");
        assertThat(audit.getDetailJson()).contains("\"versionId\":" + VERSION_V2);
    }

    @Test
    void UnpublishWritesAuditTest() {
        AppPublicationEntity publication = publication(VERSION_V1);
        publication.setStatus(AppPublicationStatus.PUBLISHED);
        org.mockito.BDDMockito.given(appPublicationEntityMapper.findActiveById(PUBLICATION_ID)).willReturn(publication);

        appPublicationApplicationService.unpublishApp(owner, APP_ID, PUBLICATION_ID);

        AuditLogEntity audit = captureAudit();
        assertThat(audit.getActionCode()).isEqualTo("MARKETPLACE_UNPUBLISH");
        assertThat(audit.getTargetId()).isEqualTo(String.valueOf(PUBLICATION_ID));
    }

    @Test
    void ArchiveAppWritesMarketplaceStateAuditTest() {
        AppPublicationEntity publication = publication(VERSION_V1);
        publication.setStatus(AppPublicationStatus.PUBLISHED);
        org.mockito.BDDMockito.given(appPublicationEntityMapper.findByAppId(APP_ID)).willReturn(publication);
        org.mockito.BDDMockito.given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .status("ACTIVE")
                .build());

        aiAppApplicationService.archiveApp(owner, APP_ID);

        AuditLogEntity audit = captureAudit();
        assertThat(audit.getActionCode()).isEqualTo("MARKETPLACE_ARCHIVE");
        assertThat(audit.getDetailJson()).contains("\"appId\":" + APP_ID);
    }

    @Test
    void MarketplaceAuditDoesNotContainStoragePathTest() {
        org.mockito.BDDMockito.given(appPublicationEntityMapper.findByAppId(APP_ID)).willReturn(null);

        appPublicationApplicationService.publishApp(owner, APP_ID, new AppPublicationCreateRequest(
                VERSION_V1, "Gate App", "desc", true, false));

        AuditLogEntity audit = captureAudit();
        assertThat(audit.getDetailJson()).doesNotContain("storagePath");
        assertThat(audit.getDetailJson()).doesNotContain("generated-exports");
    }

    @Test
    void MarketplaceAuditDoesNotContainDownloadTokenTest() {
        org.mockito.BDDMockito.given(appPublicationEntityMapper.findByAppId(APP_ID)).willReturn(null);

        appPublicationApplicationService.publishApp(owner, APP_ID, new AppPublicationCreateRequest(
                VERSION_V1, "Gate App", "desc", true, false));

        AuditLogEntity audit = captureAudit();
        assertThat(audit.getDetailJson()).doesNotContain("downloadToken");
        assertThat(audit.getDetailJson()).doesNotContain("Authorization");
    }

    private AuditLogEntity captureAudit() {
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogEntityMapper).insert(captor.capture());
        return captor.getValue();
    }

    private AppPublicationEntity publication(Long versionId) {
        return AppPublicationEntity.builder()
                .id(PUBLICATION_ID)
                .appId(APP_ID)
                .versionId(versionId)
                .status(AppPublicationStatus.PUBLISHED)
                .allowPreview(true)
                .allowDownload(true)
                .publishedAt(LocalDateTime.now())
                .build();
    }

    private AppVersionEntity version(Long versionId, int versionNo) {
        return AppVersionEntity.builder()
                .id(versionId)
                .appId(APP_ID)
                .versionNo(versionNo)
                .build();
    }
}
