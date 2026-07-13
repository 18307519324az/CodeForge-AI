package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.projection.VersionExportStatusRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AppLatestExportSummaryTest {

    private ExportPackageEntityMapper exportPackageEntityMapper;
    private AppListSummaryAggregator aggregator;

    @BeforeEach
    void setUp() {
        AppVersionEntityMapper appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        GeneratedFileEntityMapper generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        GenerationTaskEntityMapper generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        AppPublicationEntityMapper appPublicationEntityMapper = mock(AppPublicationEntityMapper.class);
        aggregator = new AppListSummaryAggregator(
                appVersionEntityMapper,
                appPublicationEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                generationTaskEntityMapper);

        given(appVersionEntityMapper.findByIds(anyList())).willReturn(List.of());
        given(generatedFileEntityMapper.countByVersionIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findRunningAppIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findLatestTaskStatusByAppIds(anyList())).willReturn(List.of());

        VersionExportStatusRow row = new VersionExportStatusRow();
        row.setAppVersionId(9001L);
        row.setStatus("PROCESSING");
        given(exportPackageEntityMapper.findLatestStatusByVersionIds(List.of(9001L))).willReturn(List.of(row));
    }

    @Test
    void shouldExposeLatestExportStatusForCurrentVersion() {
        AiAppEntity app = AiAppEntity.builder().id(3001L).currentVersionId(9001L).status("DEVELOPING").build();

        var summary = aggregator.aggregate(List.of(app)).get(3001L);

        assertThat(summary.getLatestExportStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void shouldLeaveExportStatusNullWhenNoPackage() {
        given(exportPackageEntityMapper.findLatestStatusByVersionIds(List.of(9001L))).willReturn(List.of());
        AiAppEntity app = AiAppEntity.builder().id(3001L).currentVersionId(9001L).status("DEVELOPING").build();

        var summary = aggregator.aggregate(List.of(app)).get(3001L);

        assertThat(summary.getLatestExportStatus()).isNull();
    }
}
