package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.audit.entity.AuditLogEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogEntityMapper extends BaseMapper<AuditLogEntity> {

    @Select("""
            SELECT id, workspace_id, actor_user_id, action_code, target_type, target_id,
                   request_id, detail_json, ip_address, created_at
            FROM audit_log
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AuditLogEntity> findPage(@Param("offset") long offset, @Param("limit") long limit);

    @Select("""
            SELECT COUNT(1)
            FROM audit_log
            """)
    long countAll();

    @Select("""
            SELECT id, workspace_id, actor_user_id, action_code, target_type, target_id,
                   request_id, detail_json, ip_address, created_at
            FROM audit_log
            ORDER BY created_at DESC, id DESC
            LIMIT 200
            """)
    List<AuditLogEntity> findLatestLogs();
}
