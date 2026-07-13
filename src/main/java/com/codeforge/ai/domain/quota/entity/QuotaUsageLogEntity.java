package com.codeforge.ai.domain.quota.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("quota_usage_log")
public class QuotaUsageLogEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long quotaId;
    private Long taskId;
    private String usageType;
    private Integer requestCount;
    private Integer tokenCount;
    private BigDecimal costAmount;
    private LocalDateTime createdAt;
}
