package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MetricAggregationServiceTest {

    private MetricDailyAggEntityMapper metricDailyAggEntityMapper;
    private MetricAggregationService metricAggregationService;

    @BeforeEach
    void setUp() {
        metricDailyAggEntityMapper = mock(MetricDailyAggEntityMapper.class);
        metricAggregationService = new MetricAggregationService(metricDailyAggEntityMapper);
    }

    @Test
    void shouldAggregateSuccessfulTaskMetrics() {
        LocalDate statDate = LocalDate.of(2026, 6, 24);
        given(metricDailyAggEntityMapper.findByStatDateForUpdate(statDate)).willReturn(List.of(
                metric(statDate, MetricAggregationService.REQUEST_COUNT, "2"),
                metric(statDate, MetricAggregationService.SUCCESS_COUNT, "1"),
                metric(statDate, MetricAggregationService.FAILED_COUNT, "1"),
                metric(statDate, MetricAggregationService.SUCCESS_RATE, "0.5000"),
                metric(statDate, MetricAggregationService.TOKEN_INPUT, "20"),
                metric(statDate, MetricAggregationService.TOKEN_OUTPUT, "40"),
                metric(statDate, MetricAggregationService.AVG_DURATION_MS, "1000.0000")
        ));

        metricAggregationService.recordTaskCompletion(
                GenerationTaskEntity.builder().id(9001L).build(),
                LocalDateTime.of(2026, 6, 24, 10, 0),
                2000L,
                12,
                34,
                true
        );

        verify(metricDailyAggEntityMapper).insertIgnoreMetricValue(statDate, MetricAggregationService.REQUEST_COUNT, BigDecimal.ZERO);
        verify(metricDailyAggEntityMapper).insertIgnoreMetricValue(statDate, MetricAggregationService.SUCCESS_COUNT, BigDecimal.ZERO);
        verify(metricDailyAggEntityMapper).insertIgnoreMetricValue(statDate, MetricAggregationService.FAILED_COUNT, BigDecimal.ZERO);
        verify(metricDailyAggEntityMapper).insertIgnoreMetricValue(statDate, MetricAggregationService.SUCCESS_RATE, BigDecimal.ZERO);
        verify(metricDailyAggEntityMapper).insertIgnoreMetricValue(statDate, MetricAggregationService.TOKEN_INPUT, BigDecimal.ZERO);
        verify(metricDailyAggEntityMapper).insertIgnoreMetricValue(statDate, MetricAggregationService.TOKEN_OUTPUT, BigDecimal.ZERO);
        verify(metricDailyAggEntityMapper).insertIgnoreMetricValue(statDate, MetricAggregationService.AVG_DURATION_MS, BigDecimal.ZERO);
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.REQUEST_COUNT, new BigDecimal("3"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.SUCCESS_COUNT, new BigDecimal("2"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.FAILED_COUNT, new BigDecimal("1"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.SUCCESS_RATE, new BigDecimal("0.6667"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.TOKEN_INPUT, new BigDecimal("32"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.TOKEN_OUTPUT, new BigDecimal("74"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.AVG_DURATION_MS, new BigDecimal("1333.3333"));
    }

    @Test
    void shouldAggregateFailedTaskMetrics() {
        LocalDate statDate = LocalDate.of(2026, 6, 25);
        given(metricDailyAggEntityMapper.findByStatDateForUpdate(statDate)).willReturn(List.of());

        metricAggregationService.recordTaskCompletion(
                GenerationTaskEntity.builder().id(9002L).build(),
                LocalDateTime.of(2026, 6, 25, 11, 0),
                500L,
                0,
                0,
                false
        );

        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.REQUEST_COUNT, new BigDecimal("1"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.SUCCESS_COUNT, new BigDecimal("0"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.FAILED_COUNT, new BigDecimal("1"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.SUCCESS_RATE, new BigDecimal("0.0000"));
        verify(metricDailyAggEntityMapper).upsertMetricValue(statDate, MetricAggregationService.AVG_DURATION_MS, new BigDecimal("500.0000"));
    }

    @Test
    void shouldUseTaskDatesWhenFinishedAtMissing() {
        LocalDate statDate = LocalDate.of(2026, 6, 26);
        given(metricDailyAggEntityMapper.findByStatDateForUpdate(statDate)).willReturn(List.of());

        metricAggregationService.recordTaskCompletion(
                GenerationTaskEntity.builder()
                        .id(9003L)
                        .finishedAt(LocalDateTime.of(2026, 6, 26, 12, 0))
                        .build(),
                null,
                100L,
                1,
                2,
                true
        );

        verify(metricDailyAggEntityMapper).findByStatDateForUpdate(eq(statDate));
    }

    private MetricDailyAggEntity metric(LocalDate statDate, String metricKey, String metricValue) {
        return MetricDailyAggEntity.builder()
                .statDate(statDate)
                .metricKey(metricKey)
                .metricValue(new BigDecimal(metricValue))
                .build();
    }
}
