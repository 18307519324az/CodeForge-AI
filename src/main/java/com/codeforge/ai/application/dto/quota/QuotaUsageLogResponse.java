package com.codeforge.ai.application.dto.quota;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuotaUsageLogResponse(
        Long id,
        Long quotaId,
        Long taskId,
        String usageType,
        Integer requestCount,
        Integer tokenCount,
        BigDecimal costAmount,
        LocalDateTime createdAt
) {
}
