package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AiAppDetailResponse;
import com.codeforge.ai.application.dto.app.AiAppListItemResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.projection.VersionExportStatusRow;
import com.codeforge.ai.infrastructure.persistence.projection.VersionFileCountRow;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AppListDetailConsistencyTest {

    private static final Long APP_ID = 3001L;
    private static final Long VERSION_ID = 9001L;

    private AiAppEntityMapper aiAppEntityMapper;
    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private ExportPackageEntityMapper exportPackageEntityMapper;
    private GenerationTaskEntityMapper generationTaskEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private AiAppApplicationService aiAppApplicationService;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        AppPublicationEntityMapper appPublicationEntityMapper = mock(AppPublicationEntityMapper.class);
        AppListSummaryAggregator aggregator = new AppListSummaryAggregator(
                appVersionEntityMapper,
                appPublicationEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                generationTaskEntityMapper);
        aiAppApplicationService = new AiAppApplicationService(
                aiAppEntityMapper, workspaceAccessService, aggregator,
                mock(AppPublicationApplicationService.class));
    }

    @Test
    void listAndDetailShouldExposeSameSummaryFields() {
        AiAppEntity entity = appEntity();
        stubSummaryQueries();
        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(entity);
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        given(aiAppEntityMapper.countAccessibleApps(anyList(), any(), any(), any())).willReturn(1L);
        given(aiAppEntityMapper.findAccessibleAppsPage(anyList(), any(), any(), any(), eq(0L), eq(12L)))
                .willReturn(List.of(entity));

        var query = new com.codeforge.ai.application.dto.app.AiAppQueryRequest();
        query.setPageNo(1);
        query.setPageSize(12);

        AiAppListItemResponse listItem = (AiAppListItemResponse) aiAppApplicationService
                .listApps(user(), query).records().getFirst();
        AiAppDetailResponse detail = aiAppApplicationService.getApp(user(), APP_ID);

        assertThat(listItem.currentVersionId()).isEqualTo(VERSION_ID);
        assertThat(detail.currentVersionId()).isEqualTo(VERSION_ID);
        assertThat(listItem.currentVersionNo()).isEqualTo(45);
        assertThat(detail.currentVersionNo()).isEqualTo(45);
        assertThat(listItem.latestGenerationSource()).isEqualTo("AI_DIRECT");
        assertThat(detail.latestGenerationSource()).isEqualTo("AI_DIRECT");
        assertThat(listItem.generatedFileCount()).isEqualTo(5);
        assertThat(detail.generatedFileCount()).isEqualTo(5);
        assertThat(listItem.latestExportStatus()).isEqualTo("READY");
        assertThat(detail.latestExportStatus()).isEqualTo("READY");
        assertThat(listItem.displayStatus()).isEqualTo("READY");
        assertThat(detail.displayStatus()).isEqualTo("READY");
    }

    @Test
    void shouldPopulateCurrentVersionIdFromPagedQueryEntity() {
        AiAppEntity entity = appEntity();
        stubSummaryQueries();
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        given(aiAppEntityMapper.countAccessibleApps(anyList(), any(), any(), any())).willReturn(1L);
        given(aiAppEntityMapper.findAccessibleAppsPage(anyList(), any(), any(), any(), eq(0L), eq(12L)))
                .willReturn(List.of(entity));

        var query = new com.codeforge.ai.application.dto.app.AiAppQueryRequest();
        query.setPageNo(1);
        query.setPageSize(12);

        AiAppListItemResponse listItem = (AiAppListItemResponse) aiAppApplicationService
                .listApps(user(), query).records().getFirst();

        verify(aiAppEntityMapper).findAccessibleAppsPage(anyList(), any(), any(), any(), eq(0L), eq(12L));
        assertThat(listItem.currentVersionId()).isEqualTo(VERSION_ID);
        assertThat(listItem.currentVersionNo()).isEqualTo(45);
    }

    private void stubSummaryQueries() {
        given(appVersionEntityMapper.findByIds(List.of(VERSION_ID))).willReturn(List.of(AppVersionEntity.builder()
                .id(VERSION_ID)
                .versionNo(45)
                .versionSource("AI_DIRECT")
                .build()));
        VersionFileCountRow fileCountRow = new VersionFileCountRow();
        fileCountRow.setAppVersionId(VERSION_ID);
        fileCountRow.setFileCount(5L);
        given(generatedFileEntityMapper.countByVersionIds(List.of(VERSION_ID))).willReturn(List.of(fileCountRow));
        VersionExportStatusRow exportRow = new VersionExportStatusRow();
        exportRow.setAppVersionId(VERSION_ID);
        exportRow.setStatus("READY");
        given(exportPackageEntityMapper.findLatestStatusByVersionIds(List.of(VERSION_ID)))
                .willReturn(List.of(exportRow));
        given(generationTaskEntityMapper.findRunningAppIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findLatestTaskStatusByAppIds(anyList())).willReturn(List.of());
    }

    private static AiAppEntity appEntity() {
        AiAppEntity entity = AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(1001L)
                .name("客户管理后台")
                .description("demo")
                .appType("WEB_APP")
                .status("DEVELOPING")
                .visibility("PRIVATE")
                .currentVersionId(VERSION_ID)
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 6, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 7, 6, 10, 0));
        return entity;
    }

    private static CurrentUser user() {
        return new CurrentUser(2001L, "reader", List.of("USER"));
    }
}
