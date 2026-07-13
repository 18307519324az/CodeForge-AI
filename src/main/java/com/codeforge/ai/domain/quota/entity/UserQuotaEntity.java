package com.codeforge.ai.domain.quota.entity;

import com.codeforge.ai.domain.common.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("user_quota")
public class UserQuotaEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long userId;
    private Long workspaceId;
    private Integer dailyRequestLimit;
    private Integer dailyTokenLimit;
    private BigDecimal monthlyCostLimit;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
