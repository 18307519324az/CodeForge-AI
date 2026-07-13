package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetricAggregationService {

    static final String REQUEST_COUNT = "requestCount";
    static final String SUCCESS_COUNT = "successCount";
    static final String FAILED_COUNT = "failedCount";
    static final String SUCCESS_RATE = "successRate";
    static final String TOKEN_INPUT = "tokenInput";
    static final String TOKEN_OUTPUT = "tokenOutput";
    static final String AVG_DURATION_MS = "avgDurationMs";
    private static final Set<String> METRIC_KEYS = Set.of(
            REQUEST_COUNT,
            SUCCESS_COUNT,
            FAILED_COUNT,
            SUCCESS_RATE,
            TOKEN_INPUT,
            TOKEN_OUTPUT,
            AVG_DURATION_MS
    );

    private final MetricDailyAggEntityMapper metricDailyAggEntityMapper;

    @Transactional
    public void recordTaskCompletion(GenerationTaskEntity taskEntity,
                                     LocalDateTime finishedAt,
                                     Long durationMs,
                                     Integer tokenInput,
                                     Integer tokenOutput,
                                     boolean success) {
        LocalDate statDate = resolveStatDate(taskEntity, finishedAt);
        initializeMetrics(statDate);
        List<MetricDailyAggEntity> metrics = metricDailyAggEntityMapper.findByStatDateForUpdate(statDate);
        Map<String, BigDecimal> currentValues = metrics.stream()
                .collect(Collectors.toMap(MetricDailyAggEntity::getMetricKey, MetricDailyAggEntity::getMetricValue));

        long requestCount = longValue(currentValues, REQUEST_COUNT) + 1;
        long successCount = longValue(currentValues, SUCCESS_COUNT) + (success ? 1 : 0);
        long failedCount = longValue(currentValues, FAILED_COUNT) + (success ? 0 : 1);
        long tokenInputTotal = longValue(currentValues, TOKEN_INPUT) + safeLong(tokenInput);
        long tokenOutputTotal = longValue(currentValues, TOKEN_OUTPUT) + safeLong(tokenOutput);
        long totalDuration = totalDurationBefore(currentValues) + safeLong(durationMs);
        BigDecimal successRate = BigDecimal.valueOf(successCount)
                .divide(BigDecimal.valueOf(requestCount), 4, RoundingMode.HALF_UP);
        BigDecimal avgDurationMs = BigDecimal.valueOf(totalDuration)
                .divide(BigDecimal.valueOf(requestCount), 4, RoundingMode.HALF_UP);

        upsert(statDate, REQUEST_COUNT, BigDecimal.valueOf(requestCount));
        upsert(statDate, SUCCESS_COUNT, BigDecimal.valueOf(successCount));
        upsert(statDate, FAILED_COUNT, BigDecimal.valueOf(failedCount));
        upsert(statDate, SUCCESS_RATE, successRate);
        upsert(statDate, TOKEN_INPUT, BigDecimal.valueOf(tokenInputTotal));
        upsert(statDate, TOKEN_OUTPUT, BigDecimal.valueOf(tokenOutputTotal));
        upsert(statDate, AVG_DURATION_MS, avgDurationMs);
    }

    private void initializeMetrics(LocalDate statDate) {
        for (String metricKey : METRIC_KEYS) {
            metricDailyAggEntityMapper.insertIgnoreMetricValue(statDate, metricKey, BigDecimal.ZERO);
        }
    }

    private void upsert(LocalDate statDate, String metricKey, BigDecimal metricValue) {
        metricDailyAggEntityMapper.upsertMetricValue(statDate, metricKey, metricValue);
    }

    private long totalDurationBefore(Map<String, BigDecimal> currentValues) {
        BigDecimal currentAvgDuration = currentValues.get(AVG_DURATION_MS);
        long currentRequestCount = longValue(currentValues, REQUEST_COUNT);
        if (currentAvgDuration == null || currentRequestCount <= 0) {
            return 0L;
        }
        return currentAvgDuration.multiply(BigDecimal.valueOf(currentRequestCount))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private long longValue(Map<String, BigDecimal> currentValues, String metricKey) {
        BigDecimal value = currentValues.get(metricKey);
        return value == null ? 0L : value.longValue();
    }

    private long safeLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private LocalDate resolveStatDate(GenerationTaskEntity taskEntity, LocalDateTime finishedAt) {
        if (finishedAt != null) {
            return finishedAt.toLocalDate();
        }
        if (taskEntity.getFinishedAt() != null) {
            return taskEntity.getFinishedAt().toLocalDate();
        }
        if (taskEntity.getStartedAt() != null) {
            return taskEntity.getStartedAt().toLocalDate();
        }
        return LocalDate.now();
    }
}
