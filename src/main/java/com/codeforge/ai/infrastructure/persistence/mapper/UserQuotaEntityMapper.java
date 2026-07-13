package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.quota.entity.UserQuotaEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserQuotaEntityMapper extends BaseMapper<UserQuotaEntity> {

    @Select("""
            SELECT id, user_id, workspace_id, daily_request_limit, daily_token_limit, monthly_cost_limit,
                   status, effective_from, effective_to, created_by, updated_by, created_at, updated_at, is_deleted
            FROM user_quota
            WHERE user_id = #{userId}
              AND is_deleted = 0
            ORDER BY workspace_id ASC, id ASC
            """)
    List<UserQuotaEntity> findByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id, user_id, workspace_id, daily_request_limit, daily_token_limit, monthly_cost_limit,
                   status, effective_from, effective_to, created_by, updated_by, created_at, updated_at, is_deleted
            FROM user_quota
            WHERE user_id = #{userId}
              AND workspace_id = #{workspaceId}
              AND is_deleted = 0
            LIMIT 1
            """)
    UserQuotaEntity findByUserIdAndWorkspaceId(@Param("userId") Long userId, @Param("workspaceId") Long workspaceId);

    @Update("""
            UPDATE user_quota
            SET daily_request_limit = #{dailyRequestLimit},
                daily_token_limit = #{dailyTokenLimit},
                monthly_cost_limit = #{monthlyCostLimit},
                status = #{status},
                effective_from = #{effectiveFrom},
                effective_to = #{effectiveTo},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateQuota(UserQuotaEntity entity);
}
