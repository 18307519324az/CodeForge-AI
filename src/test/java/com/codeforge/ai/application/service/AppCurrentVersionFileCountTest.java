package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.projection.VersionFileCountRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AppCurrentVersionFileCountTest {

    private AppListSummaryAggregator aggregator;

    @BeforeEach
    void setUp() {
        AppVersionEntityMapper appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        GeneratedFileEntityMapper generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        ExportPackageEntityMapper exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        GenerationTaskEntityMapper generationTaskEntityMapper = mock(GenerationTaskEntityMapper.class);
        AppPublicationEntityMapper appPublicationEntityMapper = mock(AppPublicationEntityMapper.class);
        aggregator = new AppListSummaryAggregator(
                appVersionEntityMapper,
                appPublicationEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                generationTaskEntityMapper);

        given(appVersionEntityMapper.findByIds(anyList())).willReturn(List.of());
        given(exportPackageEntityMapper.findLatestStatusByVersionIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findRunningAppIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findLatestTaskStatusByAppIds(anyList())).willReturn(List.of());

        VersionFileCountRow row = new VersionFileCountRow();
        row.setAppVersionId(9001L);
        row.setFileCount(5L);
        given(generatedFileEntityMapper.countByVersionIds(List.of(9001L))).willReturn(List.of(row));
    }

    @Test
    void shouldBindFileCountToCurrentVersion() {
        AiAppEntity app = AiAppEntity.builder().id(3001L).currentVersionId(9001L).status("DEVELOPING").build();

        var summary = aggregator.aggregate(List.of(app)).get(3001L);

        assertThat(summary.getGeneratedFileCount()).isEqualTo(5);
    }
}
