package com.codeforge.ai.application.dto.admin;

import java.time.LocalDateTime;

public record MetricRefreshResponse(
        String metricScope,
        LocalDateTime refreshedAt,
        int rebuiltDays,
        long requestCount,
        long successCount,
        long failedCount,
        String freshnessStatus
) {
}
