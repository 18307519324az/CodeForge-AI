package com.codeforge.ai.domain.metrics.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("metric_daily_agg")
public class MetricDailyAggEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private LocalDate statDate;
    private String metricKey;
    private BigDecimal metricValue;
    private String dimensionsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
