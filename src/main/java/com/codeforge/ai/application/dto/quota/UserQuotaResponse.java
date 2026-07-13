package com.codeforge.ai.application.dto.quota;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserQuotaResponse(
        Long id,
        Long userId,
        Long workspaceId,
        Integer dailyRequestLimit,
        Integer dailyTokenLimit,
        BigDecimal monthlyCostLimit,
        String status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        LocalDateTime updatedAt
) {
}
