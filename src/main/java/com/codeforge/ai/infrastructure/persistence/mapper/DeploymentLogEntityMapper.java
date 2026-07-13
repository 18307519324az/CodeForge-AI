package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.deploy.entity.DeploymentLogEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeploymentLogEntityMapper extends BaseMapper<DeploymentLogEntity> {

    @Select("""
            SELECT id, deployment_job_id, log_level, log_message, log_time
            FROM deployment_log
            WHERE deployment_job_id = #{deploymentJobId}
            ORDER BY log_time ASC, id ASC
            """)
    List<DeploymentLogEntity> findByDeploymentJobId(@Param("deploymentJobId") Long deploymentJobId);
}
