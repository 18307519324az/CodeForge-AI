package com.codeforge.ai.application.config;

import java.time.ZoneId;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codeforge.admin.metrics")
public class AdminMetricsProperties {

    /**
     * Daily aggregation buckets: mark overview stale when source data is older than this threshold.
     */
    private long staleAfterSeconds = 129_600L;

    /**
     * Hourly refresh aligned with daily buckets (Asia/Shanghai calendar dates).
     */
    private String schedulerCron = "0 0 * * * ?";

    private String zoneId = "Asia/Shanghai";

    public ZoneId resolvedZoneId() {
        return ZoneId.of(zoneId);
    }
}
