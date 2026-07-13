package com.codeforge.ai.application.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MetricSummaryResponse(
        LocalDate statDate,
        Long requestCount,
        Long successCount,
        Long failedCount,
        BigDecimal successRate,
        Long tokenInput,
        Long tokenOutput,
        Long avgDurationMs,
        String metricScope,
        LocalDateTime dataAsOf,
        LocalDateTime generatedAt,
        String freshnessStatus,
        Long staleAfterSeconds
) {
    public static final String MODEL_CALL_ALL_TIME_SCOPE = "MODEL_CALL_ALL_TIME";
}
