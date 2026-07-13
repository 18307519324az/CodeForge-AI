package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.deploy.entity.DeploymentJobEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeploymentJobEntityMapper extends BaseMapper<DeploymentJobEntity> {

    @Select("""
            SELECT id, app_id, app_version_id, environment_code, deploy_target, deploy_status,
                   runtime_config_json, request_id, started_at, finished_at,
                   created_by, updated_by, created_at, updated_at, is_deleted
            FROM deployment_job
            WHERE id = #{deploymentJobId}
              AND is_deleted = 0
            LIMIT 1
            """)
    DeploymentJobEntity findById(@Param("deploymentJobId") Long deploymentJobId);
}
