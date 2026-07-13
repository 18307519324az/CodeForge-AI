package com.codeforge.ai.application.dto.admin;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class QuotaAdjustRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long workspaceId;

    private Integer dailyRequestLimit;

    private Integer dailyTokenLimit;

    private BigDecimal monthlyCostLimit;
}
