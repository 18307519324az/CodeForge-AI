package com.codeforge.ai.infrastructure.scheduler;

import com.codeforge.ai.application.service.AdminMetricsApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricDailyAggregationScheduler {

    private final AdminMetricsApplicationService adminMetricsApplicationService;

    @Scheduled(cron = "${codeforge.admin.metrics.scheduler-cron:0 0 * * * ?}")
    public void refreshRecentModelCallMetrics() {
        try {
            adminMetricsApplicationService.refreshRecentMetrics();
        } catch (Exception exception) {
            log.warn("Scheduled model call metric refresh failed: {}", exception.getMessage());
        }
    }
}
