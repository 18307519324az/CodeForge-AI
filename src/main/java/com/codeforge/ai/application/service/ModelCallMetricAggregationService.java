package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.ModelCallOverviewMetrics;
import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModelCallMetricAggregationService {

    static final String REQUEST_COUNT = MetricAggregationService.REQUEST_COUNT;
    static final String SUCCESS_COUNT = MetricAggregationService.SUCCESS_COUNT;
    static final String FAILED_COUNT = MetricAggregationService.FAILED_COUNT;
    static final String SUCCESS_RATE = MetricAggregationService.SUCCESS_RATE;
    static final String TOKEN_INPUT = MetricAggregationService.TOKEN_INPUT;
    static final String TOKEN_OUTPUT = MetricAggregationService.TOKEN_OUTPUT;
    static final String AVG_DURATION_MS = MetricAggregationService.AVG_DURATION_MS;

    private static final Set<String> METRIC_KEYS = Set.of(
            REQUEST_COUNT,
            SUCCESS_COUNT,
            FAILED_COUNT,
            SUCCESS_RATE,
            TOKEN_INPUT,
            TOKEN_OUTPUT,
            AVG_DURATION_MS
    );

    private final ModelCallLogEntityMapper modelCallLogEntityMapper;
    private final MetricDailyAggEntityMapper metricDailyAggEntityMapper;

    @Transactional
    public void rebuildForStatDate(LocalDate statDate) {
        ModelCallOverviewMetrics metrics = modelCallLogEntityMapper.aggregateFinalizedCallsByDate(statDate);
        if (metrics == null || metrics.getRequestCount() <= 0) {
            return;
        }
        writeDailyMetrics(statDate, metrics);
    }

    @Transactional
    public int rebuildAllCallDates() {
        List<LocalDate> dates = modelCallLogEntityMapper.findDistinctFinalizedCallDates();
        for (LocalDate statDate : dates) {
            ModelCallOverviewMetrics metrics = modelCallLogEntityMapper.aggregateFinalizedCallsByDate(statDate);
            if (metrics != null && metrics.getRequestCount() > 0) {
                writeDailyMetrics(statDate, metrics);
            }
        }
        return dates.size();
    }

    @Transactional
    public int rebuildRecentCallDates(LocalDate today) {
        List<LocalDate> dates = modelCallLogEntityMapper.findDistinctFinalizedCallDates();
        int rebuilt = 0;
        for (LocalDate statDate : dates) {
            if (!statDate.isBefore(today.minusDays(1))) {
                ModelCallOverviewMetrics metrics = modelCallLogEntityMapper.aggregateFinalizedCallsByDate(statDate);
                if (metrics != null && metrics.getRequestCount() > 0) {
                    writeDailyMetrics(statDate, metrics);
                    rebuilt++;
                }
            }
        }
        return rebuilt;
    }

    ModelCallOverviewMetrics aggregateAllTimeFromDailyBuckets() {
        List<MetricDailyAggEntity> rows = metricDailyAggEntityMapper.findAllSummaryMetrics();
        if (rows.isEmpty()) {
            ModelCallOverviewMetrics empty = new ModelCallOverviewMetrics();
            empty.setRequestCount(0L);
            empty.setSuccessCount(0L);
            empty.setFailedCount(0L);
            empty.setTokenInput(0L);
            empty.setTokenOutput(0L);
            empty.setAvgDurationMs(0L);
            empty.setDataAsOf(null);
            return empty;
        }

        Map<LocalDate, Map<String, BigDecimal>> byDate = rows.stream()
                .collect(Collectors.groupingBy(
                        MetricDailyAggEntity::getStatDate,
                        Collectors.toMap(MetricDailyAggEntity::getMetricKey, MetricDailyAggEntity::getMetricValue, (a, b) -> a)));

        long requestCount = 0L;
        long successCount = 0L;
        long failedCount = 0L;
        long tokenInput = 0L;
        long tokenOutput = 0L;
        long weightedDurationTotal = 0L;

        for (Map<String, BigDecimal> dayMetrics : byDate.values()) {
            long dayRequests = longValue(dayMetrics, REQUEST_COUNT);
            requestCount += dayRequests;
            successCount += longValue(dayMetrics, SUCCESS_COUNT);
            failedCount += longValue(dayMetrics, FAILED_COUNT);
            tokenInput += longValue(dayMetrics, TOKEN_INPUT);
            tokenOutput += longValue(dayMetrics, TOKEN_OUTPUT);
            weightedDurationTotal += Math.round(decimalValue(dayMetrics, AVG_DURATION_MS).doubleValue() * dayRequests);
        }

        long avgDurationMs = requestCount <= 0 ? 0L : Math.round((double) weightedDurationTotal / requestCount);
        ModelCallOverviewMetrics metrics = new ModelCallOverviewMetrics();
        metrics.setRequestCount(requestCount);
        metrics.setSuccessCount(successCount);
        metrics.setFailedCount(failedCount);
        metrics.setTokenInput(tokenInput);
        metrics.setTokenOutput(tokenOutput);
        metrics.setAvgDurationMs(avgDurationMs);
        metrics.setDataAsOf(modelCallLogEntityMapper.findLatestFinalizedCallAt());
        return metrics;
    }

    private void writeDailyMetrics(LocalDate statDate, ModelCallOverviewMetrics metrics) {
        initializeMetrics(statDate);
        metricDailyAggEntityMapper.findByStatDateForUpdate(statDate);

        BigDecimal successRate = metrics.getRequestCount() <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(metrics.getSuccessCount())
                        .divide(BigDecimal.valueOf(metrics.getRequestCount()), 4, RoundingMode.HALF_UP);

        upsert(statDate, REQUEST_COUNT, BigDecimal.valueOf(metrics.getRequestCount()));
        upsert(statDate, SUCCESS_COUNT, BigDecimal.valueOf(metrics.getSuccessCount()));
        upsert(statDate, FAILED_COUNT, BigDecimal.valueOf(metrics.getFailedCount()));
        upsert(statDate, SUCCESS_RATE, successRate);
        upsert(statDate, TOKEN_INPUT, BigDecimal.valueOf(metrics.getTokenInput()));
        upsert(statDate, TOKEN_OUTPUT, BigDecimal.valueOf(metrics.getTokenOutput()));
        upsert(statDate, AVG_DURATION_MS, BigDecimal.valueOf(metrics.getAvgDurationMs()));
    }

    private void initializeMetrics(LocalDate statDate) {
        for (String metricKey : METRIC_KEYS) {
            metricDailyAggEntityMapper.insertIgnoreMetricValue(statDate, metricKey, BigDecimal.ZERO);
        }
    }

    private void upsert(LocalDate statDate, String metricKey, BigDecimal metricValue) {
        metricDailyAggEntityMapper.upsertMetricValue(statDate, metricKey, metricValue);
    }

    private long longValue(Map<String, BigDecimal> metrics, String metricKey) {
        BigDecimal value = metrics.get(metricKey);
        return value == null ? 0L : value.longValue();
    }

    private BigDecimal decimalValue(Map<String, BigDecimal> metrics, String metricKey) {
        BigDecimal value = metrics.get(metricKey);
        return value == null ? BigDecimal.ZERO : value;
    }
}
