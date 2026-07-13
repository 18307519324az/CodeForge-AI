package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.ModelCallOverviewMetrics;
import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ModelCallMetricAggregationServiceTest {

    private ModelCallLogEntityMapper modelCallLogEntityMapper;
    private MetricDailyAggEntityMapper metricDailyAggEntityMapper;
    private ModelCallMetricAggregationService modelCallMetricAggregationService;

    @BeforeEach
    void setUp() {
        modelCallLogEntityMapper = mock(ModelCallLogEntityMapper.class);
        metricDailyAggEntityMapper = mock(MetricDailyAggEntityMapper.class);
        modelCallMetricAggregationService = new ModelCallMetricAggregationService(
                modelCallLogEntityMapper,
                metricDailyAggEntityMapper
        );
    }

    @Test
    void shouldRebuildDailyMetricsFromModelCallLog() {
        LocalDate statDate = LocalDate.of(2026, 7, 11);
        ModelCallOverviewMetrics metrics = new ModelCallOverviewMetrics();
        metrics.setRequestCount(3L);
        metrics.setSuccessCount(3L);
        metrics.setFailedCount(0L);
        metrics.setTokenInput(30L);
        metrics.setTokenOutput(60L);
        metrics.setAvgDurationMs(120L);
        metrics.setDataAsOf(LocalDateTime.of(2026, 7, 11, 18, 29, 29));

        given(modelCallLogEntityMapper.aggregateFinalizedCallsByDate(statDate)).willReturn(metrics);
        given(metricDailyAggEntityMapper.findByStatDateForUpdate(statDate)).willReturn(List.of());

        modelCallMetricAggregationService.rebuildForStatDate(statDate);

        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, ModelCallMetricAggregationService.REQUEST_COUNT, new BigDecimal("3"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, ModelCallMetricAggregationService.SUCCESS_COUNT, new BigDecimal("3"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, ModelCallMetricAggregationService.FAILED_COUNT, new BigDecimal("0"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, ModelCallMetricAggregationService.SUCCESS_RATE, new BigDecimal("1.0000"));
    }

    @Test
    void shouldAggregateAllTimeFromDailyBuckets() {
        LocalDate dayOne = LocalDate.of(2026, 7, 10);
        LocalDate dayTwo = LocalDate.of(2026, 7, 11);
        given(metricDailyAggEntityMapper.findAllSummaryMetrics()).willReturn(List.of(
                metric(dayOne, ModelCallMetricAggregationService.REQUEST_COUNT, "10"),
                metric(dayOne, ModelCallMetricAggregationService.SUCCESS_COUNT, "8"),
                metric(dayOne, ModelCallMetricAggregationService.FAILED_COUNT, "2"),
                metric(dayOne, ModelCallMetricAggregationService.AVG_DURATION_MS, "100"),
                metric(dayTwo, ModelCallMetricAggregationService.REQUEST_COUNT, "5"),
                metric(dayTwo, ModelCallMetricAggregationService.SUCCESS_COUNT, "4"),
                metric(dayTwo, ModelCallMetricAggregationService.FAILED_COUNT, "1"),
                metric(dayTwo, ModelCallMetricAggregationService.AVG_DURATION_MS, "200")
        ));
        given(modelCallLogEntityMapper.findLatestFinalizedCallAt())
                .willReturn(LocalDateTime.of(2026, 7, 11, 18, 0));

        ModelCallOverviewMetrics aggregated = modelCallMetricAggregationService.aggregateAllTimeFromDailyBuckets();

        assertThat(aggregated.getRequestCount()).isEqualTo(15L);
        assertThat(aggregated.getSuccessCount()).isEqualTo(12L);
        assertThat(aggregated.getFailedCount()).isEqualTo(3L);
        assertThat(aggregated.getAvgDurationMs()).isEqualTo(133L);
    }

    private MetricDailyAggEntity metric(LocalDate statDate, String metricKey, String metricValue) {
        return MetricDailyAggEntity.builder()
                .statDate(statDate)
                .metricKey(metricKey)
                .metricValue(new BigDecimal(metricValue))
                .build();
    }
}
