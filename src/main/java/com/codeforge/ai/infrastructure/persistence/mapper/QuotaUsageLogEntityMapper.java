package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.quota.entity.QuotaUsageLogEntity;
import com.mybatisflex.core.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuotaUsageLogEntityMapper extends BaseMapper<QuotaUsageLogEntity> {

    @Select("""
            SELECT id, quota_id, task_id, usage_type, request_count, token_count, cost_amount, created_at
            FROM quota_usage_log
            WHERE quota_id = #{quotaId}
              AND created_at >= #{createdFrom}
            """)
    List<QuotaUsageLogEntity> findByQuotaIdSince(@Param("quotaId") Long quotaId,
                                                 @Param("createdFrom") LocalDateTime createdFrom);

    @Select("""
            SELECT id, quota_id, task_id, usage_type, request_count, token_count, cost_amount, created_at
            FROM quota_usage_log
            ORDER BY created_at DESC, id DESC
            LIMIT 200
            """)
    List<QuotaUsageLogEntity> findLatestLogs();
}
