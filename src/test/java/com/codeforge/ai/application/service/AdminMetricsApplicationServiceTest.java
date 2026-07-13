package com.codeforge.ai.application.service;

import com.codeforge.ai.application.config.AdminMetricsProperties;
import com.codeforge.ai.application.dto.admin.MetricFreshnessStatus;
import com.codeforge.ai.application.dto.admin.MetricSummaryResponse;
import com.codeforge.ai.application.dto.admin.ModelCallOverviewMetrics;
import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.codeforge.ai.infrastructure.audit.AuditLogWriter;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminMetricsApplicationServiceTest {

    private AdminMetricsProperties adminMetricsProperties;
    private ModelCallMetricAggregationService modelCallMetricAggregationService;
    private ModelCallLogEntityMapper modelCallLogEntityMapper;
    private MetricDailyAggEntityMapper metricDailyAggEntityMapper;
    private AuditLogWriter auditLogWriter;
    private AdminMetricsApplicationService adminMetricsApplicationService;

    @BeforeEach
    void setUp() {
        adminMetricsProperties = new AdminMetricsProperties();
        modelCallMetricAggregationService = mock(ModelCallMetricAggregationService.class);
        modelCallLogEntityMapper = mock(ModelCallLogEntityMapper.class);
        metricDailyAggEntityMapper = mock(MetricDailyAggEntityMapper.class);
        auditLogWriter = mock(AuditLogWriter.class);
        adminMetricsApplicationService = new AdminMetricsApplicationService(
                adminMetricsProperties,
                modelCallMetricAggregationService,
                modelCallLogEntityMapper,
                metricDailyAggEntityMapper,
                auditLogWriter
        );
    }

    @Test
    void shouldReturnSourceTruthWithFreshnessMetadata() {
        LocalDateTime dataAsOf = LocalDateTime.of(2026, 7, 11, 18, 29, 29);
        given(modelCallLogEntityMapper.aggregateAllFinalizedCalls()).willReturn(overview(147L, 135L, 12L, dataAsOf));
        given(modelCallMetricAggregationService.aggregateAllTimeFromDailyBuckets()).willReturn(overview(45L, 38L, 7L, dataAsOf));
        given(metricDailyAggEntityMapper.findLatestAggregationUpdatedAt()).willReturn(LocalDateTime.of(2026, 6, 28, 18, 46, 13));
        given(metricDailyAggEntityMapper.findLatestStatDate()).willReturn(LocalDate.of(2026, 6, 28));

        MetricSummaryResponse response = adminMetricsApplicationService.getMetricsSummary(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));

        assertThat(response.requestCount()).isEqualTo(147L);
        assertThat(response.successCount()).isEqualTo(135L);
        assertThat(response.failedCount()).isEqualTo(12L);
        assertThat(response.metricScope()).isEqualTo(MetricSummaryResponse.MODEL_CALL_ALL_TIME_SCOPE);
        assertThat(response.dataAsOf()).isEqualTo(dataAsOf);
        assertThat(response.freshnessStatus()).isEqualTo(MetricFreshnessStatus.STALE.name());
        assertThat(response.generatedAt()).isNotNull();
    }

    @Test
    void shouldReturnEmptySummaryWhenNoFinalizedCalls() {
        given(modelCallLogEntityMapper.aggregateAllFinalizedCalls()).willReturn(overview(0L, 0L, 0L, null));

        MetricSummaryResponse response = adminMetricsApplicationService.getMetricsSummary(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));

        assertThat(response.requestCount()).isZero();
        assertThat(response.freshnessStatus()).isEqualTo(MetricFreshnessStatus.EMPTY.name());
    }

    @Test
    void shouldRejectNonAdminMetricsQuery() {
        assertThatThrownBy(() -> adminMetricsApplicationService.getMetricsSummary(
                new CurrentUser(2L, "user", List.of("USER"))))
                .isInstanceOf(com.codeforge.ai.shared.exception.BusinessException.class);
    }

    @Test
    void shouldRefreshMetricsAndWriteAuditLog() {
        LocalDateTime refreshedAt = LocalDateTime.of(2026, 7, 11, 20, 0);
        given(modelCallMetricAggregationService.rebuildAllCallDates()).willReturn(8);
        given(modelCallMetricAggregationService.aggregateAllTimeFromDailyBuckets())
                .willReturn(overview(147L, 135L, 12L, refreshedAt));
        given(metricDailyAggEntityMapper.findLatestAggregationUpdatedAt()).willReturn(refreshedAt);
        given(modelCallLogEntityMapper.findLatestFinalizedCallAt()).willReturn(refreshedAt);

        var response = adminMetricsApplicationService.refreshMetrics(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));

        assertThat(response.rebuiltDays()).isEqualTo(8);
        assertThat(response.requestCount()).isEqualTo(147L);
        assertThat(response.freshnessStatus()).isEqualTo(MetricFreshnessStatus.FRESH.name());
        verify(auditLogWriter).insert(any());
    }

    private ModelCallOverviewMetrics overview(long request, long success, long failed, LocalDateTime dataAsOf) {
        ModelCallOverviewMetrics metrics = new ModelCallOverviewMetrics();
        metrics.setRequestCount(request);
        metrics.setSuccessCount(success);
        metrics.setFailedCount(failed);
        metrics.setTokenInput(100L);
        metrics.setTokenOutput(200L);
        metrics.setAvgDurationMs(500L);
        metrics.setDataAsOf(dataAsOf);
        return metrics;
    }
}
