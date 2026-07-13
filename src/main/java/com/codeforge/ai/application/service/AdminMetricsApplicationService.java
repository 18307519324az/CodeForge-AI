package com.codeforge.ai.application.service;

import com.codeforge.ai.application.config.AdminMetricsProperties;
import com.codeforge.ai.application.dto.admin.MetricFreshnessStatus;
import com.codeforge.ai.application.dto.admin.MetricRefreshResponse;
import com.codeforge.ai.application.dto.admin.MetricSummaryResponse;
import com.codeforge.ai.application.dto.admin.ModelCallOverviewMetrics;
import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMetricsApplicationService {

    private final AdminMetricsProperties adminMetricsProperties;
    private final ModelCallMetricAggregationService modelCallMetricAggregationService;
    private final ModelCallLogEntityMapper modelCallLogEntityMapper;
    private final MetricDailyAggEntityMapper metricDailyAggEntityMapper;
    private final AuditLogWriter auditLogWriter;

    public MetricSummaryResponse getMetricsSummary(CurrentUser currentUser) {
        requirePlatformAdmin(currentUser);
        LocalDateTime generatedAt = LocalDateTime.now();
        ModelCallOverviewMetrics sourceMetrics = modelCallLogEntityMapper.aggregateAllFinalizedCalls();
        if (sourceMetrics == null || sourceMetrics.getRequestCount() <= 0) {
            return emptyMetricSummary(generatedAt);
        }

        ModelCallOverviewMetrics aggregatedMetrics = modelCallMetricAggregationService.aggregateAllTimeFromDailyBuckets();
        MetricFreshnessStatus freshnessStatus = resolveFreshnessStatus(
                sourceMetrics.getDataAsOf(),
                metricDailyAggEntityMapper.findLatestAggregationUpdatedAt(),
                aggregatedMetrics.getRequestCount() > 0);

        return new MetricSummaryResponse(
                metricDailyAggEntityMapper.findLatestStatDate(),
                sourceMetrics.getRequestCount(),
                sourceMetrics.getSuccessCount(),
                sourceMetrics.getFailedCount(),
                formatSuccessRate(sourceMetrics.getRequestCount(), sourceMetrics.getSuccessCount()),
                sourceMetrics.getTokenInput(),
                sourceMetrics.getTokenOutput(),
                sourceMetrics.getAvgDurationMs(),
                MetricSummaryResponse.MODEL_CALL_ALL_TIME_SCOPE,
                sourceMetrics.getDataAsOf(),
                generatedAt,
                freshnessStatus.name(),
                adminMetricsProperties.getStaleAfterSeconds()
        );
    }

    @Transactional
    public MetricRefreshResponse refreshMetrics(CurrentUser currentUser) {
        requirePlatformAdmin(currentUser);
        int rebuiltDays = modelCallMetricAggregationService.rebuildAllCallDates();
        ModelCallOverviewMetrics metrics = modelCallMetricAggregationService.aggregateAllTimeFromDailyBuckets();
        LocalDateTime refreshedAt = metricDailyAggEntityMapper.findLatestAggregationUpdatedAt();
        MetricFreshnessStatus freshnessStatus = resolveFreshnessStatus(
                modelCallLogEntityMapper.findLatestFinalizedCallAt(),
                refreshedAt,
                metrics.getRequestCount() > 0);

        auditLogWriter.insert(AuditLogEntity.builder()
                .actorUserId(currentUser.requiredUserId())
                .actionCode("METRICS_REFRESH")
                .targetType("METRIC_DAILY_AGG")
                .targetId("MODEL_CALL_ALL_TIME")
                .detailJson("{\"rebuiltDays\":" + rebuiltDays + ",\"requestCount\":" + metrics.getRequestCount() + "}")
                .build());

        return new MetricRefreshResponse(
                MetricSummaryResponse.MODEL_CALL_ALL_TIME_SCOPE,
                refreshedAt,
                rebuiltDays,
                metrics.getRequestCount(),
                metrics.getSuccessCount(),
                metrics.getFailedCount(),
                freshnessStatus.name()
        );
    }

    public void refreshRecentMetrics() {
        ZoneId zoneId = adminMetricsProperties.resolvedZoneId();
        LocalDate today = LocalDate.now(zoneId);
        modelCallMetricAggregationService.rebuildRecentCallDates(today);
    }

    private MetricSummaryResponse emptyMetricSummary(LocalDateTime generatedAt) {
        return new MetricSummaryResponse(
                null,
                0L,
                0L,
                0L,
                BigDecimal.ZERO,
                0L,
                0L,
                0L,
                MetricSummaryResponse.MODEL_CALL_ALL_TIME_SCOPE,
                null,
                generatedAt,
                MetricFreshnessStatus.EMPTY.name(),
                adminMetricsProperties.getStaleAfterSeconds()
        );
    }

    private BigDecimal formatSuccessRate(long requestCount, long successCount) {
        if (requestCount <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(successCount)
                .divide(BigDecimal.valueOf(requestCount), 4, RoundingMode.HALF_UP);
    }

    private MetricFreshnessStatus resolveFreshnessStatus(LocalDateTime sourceDataAsOf,
                                                         LocalDateTime aggregationUpdatedAt,
                                                         boolean hasAggregation) {
        if (sourceDataAsOf == null) {
            return MetricFreshnessStatus.EMPTY;
        }
        if (!hasAggregation || aggregationUpdatedAt == null) {
            return MetricFreshnessStatus.STALE;
        }
        long ageSeconds = Math.max(0L, Duration.between(aggregationUpdatedAt, sourceDataAsOf).getSeconds());
        if (ageSeconds > adminMetricsProperties.getStaleAfterSeconds()) {
            return MetricFreshnessStatus.STALE;
        }
        return MetricFreshnessStatus.FRESH;
    }

    private void requirePlatformAdmin(CurrentUser currentUser) {
        if (!currentUser.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }
}
