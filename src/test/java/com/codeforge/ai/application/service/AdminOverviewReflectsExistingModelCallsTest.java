package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.admin.MetricSummaryResponse;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.MetricDailyAggEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "mybatis-flex.configuration.map-underscore-to-camel-case=false"
})
class AdminOverviewReflectsExistingModelCallsTest {

    @Autowired
    private ModelCallLogEntityMapper modelCallLogEntityMapper;

    @Autowired
    private MetricDailyAggEntityMapper metricDailyAggEntityMapper;

    @Autowired
    private AdminMetricsApplicationService adminMetricsApplicationService;

    @Autowired
    private ModelCallMetricAggregationService modelCallMetricAggregationService;

    @Test
    void getMetricsSummaryShouldReflectModelCallLogSourceTruth() {
        long baselineRequests = modelCallLogEntityMapper.countAllLogs();
        long baselineSuccess = modelCallLogEntityMapper.countByStatus("SUCCESS");
        long baselineFailed = modelCallLogEntityMapper.countByStatus("FAILED");

        insertCall("SUCCESS");
        insertCall("SUCCESS");
        insertCall("FAILED");

        MetricSummaryResponse beforeRefresh = adminMetricsApplicationService.getMetricsSummary(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));

        assertThat(beforeRefresh.requestCount()).isEqualTo(baselineRequests + 3L);
        assertThat(beforeRefresh.successCount()).isEqualTo(baselineSuccess + 2L);
        assertThat(beforeRefresh.failedCount()).isEqualTo(baselineFailed + 1L);
        assertThat(beforeRefresh.metricScope()).isEqualTo(MetricSummaryResponse.MODEL_CALL_ALL_TIME_SCOPE);
        assertThat(beforeRefresh.dataAsOf()).isNotNull();

        modelCallMetricAggregationService.rebuildAllCallDates();
        MetricSummaryResponse afterRefresh = adminMetricsApplicationService.getMetricsSummary(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));

        assertThat(afterRefresh.requestCount()).isEqualTo(baselineRequests + 3L);
        assertThat(afterRefresh.freshnessStatus()).isEqualTo("FRESH");
        assertThat(metricDailyAggEntityMapper.findLatestAggregationUpdatedAt()).isNotNull();
    }

    private void insertCall(String status) {
        modelCallLogEntityMapper.insertCallLog(ModelCallLogEntity.builder()
                .taskId(1L)
                .appId(1L)
                .providerId(1L)
                .providerCode("openai")
                .modelName("gpt-4.1")
                .apiProtocol("OPENAI_COMPATIBLE")
                .status(status)
                .inputTokens(10)
                .outputTokens(20)
                .durationMs(100L)
                .fallbackUsed(false)
                .generationSource("AI_DIRECT")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
