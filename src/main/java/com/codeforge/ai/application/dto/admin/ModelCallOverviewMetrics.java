package com.codeforge.ai.application.dto.admin;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ModelCallOverviewMetrics {

    private Long requestCount;
    private Long successCount;
    private Long failedCount;
    private Long tokenInput;
    private Long tokenOutput;
    private Long avgDurationMs;
    private LocalDateTime dataAsOf;

    public long getRequestCount() {
        return requestCount == null ? 0L : requestCount;
    }

    public long getSuccessCount() {
        return successCount == null ? 0L : successCount;
    }

    public long getFailedCount() {
        return failedCount == null ? 0L : failedCount;
    }

    public long getTokenInput() {
        return tokenInput == null ? 0L : tokenInput;
    }

    public long getTokenOutput() {
        return tokenOutput == null ? 0L : tokenOutput;
    }

    public long getAvgDurationMs() {
        return avgDurationMs == null ? 0L : avgDurationMs;
    }
}
