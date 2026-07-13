package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.projection.AppLatestTaskStatusRow;
import com.codeforge.ai.infrastructure.persistence.projection.VersionExportStatusRow;
import com.codeforge.ai.infrastructure.persistence.projection.VersionFileCountRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AppListSummaryAggregationTest {

    private AppVersionEntityMapper appVersionEntityMapper;
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    private ExportPackageEntityMapper exportPackageEntityMapper;
    private GenerationTaskEntityMapper generationTaskEntityMapper;
    private AppPublicationEntityMapper appPublicationEntityMapper;
    private AppListSummaryAggregator aggregator;

    @BeforeEach
    void setUp() {
        appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        appPublicationEntityMapper = mock(AppPublicationEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        aggregator = new AppListSummaryAggregator(
                appVersionEntityMapper,
                appPublicationEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                generationTaskEntityMapper);
    }

    @Test
    void shouldAggregateSummariesWithFixedBatchQueries() {
        AiAppEntity app = app(3001L, 9001L);
        stubVersion(9001L, 45, "AI_DIRECT");
        stubFileCount(9001L, 5L);
        stubExport(9001L, "READY");
        given(generationTaskEntityMapper.findRunningAppIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findLatestTaskStatusByAppIds(anyList())).willReturn(List.of());

        var summaries = aggregator.aggregate(List.of(app));

        verify(appVersionEntityMapper, times(1)).findByIds(List.of(9001L));
        verify(generatedFileEntityMapper, times(1)).countByVersionIds(List.of(9001L));
        verify(exportPackageEntityMapper, times(1)).findLatestStatusByVersionIds(List.of(9001L));
        verify(generationTaskEntityMapper, times(1)).findRunningAppIds(List.of(3001L));
        verify(generationTaskEntityMapper, times(1)).findLatestTaskStatusByAppIds(List.of(3001L));
        assertThat(summaries.get(3001L).getCurrentVersionNo()).isEqualTo(45);
        assertThat(summaries.get(3001L).getLatestGenerationSource()).isEqualTo("AI_DIRECT");
        assertThat(summaries.get(3001L).getGeneratedFileCount()).isEqualTo(5);
        assertThat(summaries.get(3001L).getLatestExportStatus()).isEqualTo("READY");
        assertThat(summaries.get(3001L).getDisplayStatus()).isEqualTo("READY");
    }

    @Test
    void shouldSkipVersionQueriesWhenAppsHaveNoCurrentVersion() {
        AiAppEntity app = app(3002L, null);
        given(generationTaskEntityMapper.findRunningAppIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findLatestTaskStatusByAppIds(anyList())).willReturn(List.of());

        var summaries = aggregator.aggregate(List.of(app));

        verify(appVersionEntityMapper, times(0)).findByIds(any());
        verify(generatedFileEntityMapper, times(0)).countByVersionIds(any());
        verify(exportPackageEntityMapper, times(0)).findLatestStatusByVersionIds(any());
        assertThat(summaries.get(3002L).getCurrentVersionNo()).isNull();
        assertThat(summaries.get(3002L).getDisplayStatus()).isEqualTo("DRAFT");
    }

    private void stubVersion(Long versionId, int versionNo, String source) {
        given(appVersionEntityMapper.findByIds(anyList())).willReturn(List.of(AppVersionEntity.builder()
                .id(versionId)
                .versionNo(versionNo)
                .versionSource(source)
                .build()));
    }

    private void stubFileCount(Long versionId, long count) {
        VersionFileCountRow row = new VersionFileCountRow();
        row.setAppVersionId(versionId);
        row.setFileCount(count);
        given(generatedFileEntityMapper.countByVersionIds(anyList())).willReturn(List.of(row));
    }

    private void stubExport(Long versionId, String status) {
        VersionExportStatusRow row = new VersionExportStatusRow();
        row.setAppVersionId(versionId);
        row.setStatus(status);
        given(exportPackageEntityMapper.findLatestStatusByVersionIds(anyList())).willReturn(List.of(row));
    }

    private static AiAppEntity app(Long appId, Long versionId) {
        return AiAppEntity.builder()
                .id(appId)
                .workspaceId(1001L)
                .name("Demo")
                .status("DEVELOPING")
                .currentVersionId(versionId)
                .build();
    }
}
