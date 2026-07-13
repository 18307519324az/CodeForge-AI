package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AppGenerationSourceSummaryTest {

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

        given(appVersionEntityMapper.findByIds(anyList())).willReturn(List.of(
                AppVersionEntity.builder().id(9001L).versionNo(1).versionSource("RULE_FALLBACK").build(),
                AppVersionEntity.builder().id(9002L).versionNo(2).versionSource("RULE_ONLY").build()
        ));
        given(generatedFileEntityMapper.countByVersionIds(anyList())).willReturn(List.of());
        given(exportPackageEntityMapper.findLatestStatusByVersionIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findRunningAppIds(anyList())).willReturn(List.of());
        given(generationTaskEntityMapper.findLatestTaskStatusByAppIds(anyList())).willReturn(List.of());
    }

    @Test
    void shouldUseVersionSourceAsGenerationSummary() {
        AiAppEntity fallbackApp = AiAppEntity.builder().id(1L).currentVersionId(9001L).status("DEVELOPING").build();
        AiAppEntity ruleOnlyApp = AiAppEntity.builder().id(2L).currentVersionId(9002L).status("DEVELOPING").build();

        var summaries = aggregator.aggregate(List.of(fallbackApp, ruleOnlyApp));

        assertThat(summaries.get(1L).getLatestGenerationSource()).isEqualTo("RULE_FALLBACK");
        assertThat(summaries.get(2L).getLatestGenerationSource()).isEqualTo("RULE_ONLY");
    }
}
