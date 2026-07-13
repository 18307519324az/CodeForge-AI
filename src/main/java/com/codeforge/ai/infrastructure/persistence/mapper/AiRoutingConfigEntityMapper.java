package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.model.entity.AiRoutingConfigEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiRoutingConfigEntityMapper extends BaseMapper<AiRoutingConfigEntity> {

    @Select("SELECT id, routing_mode AS routingMode, pinned_provider_code AS pinnedProviderCode, "
            + "updated_by AS updatedBy, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM ai_routing_config WHERE id = #{id} AND is_deleted = 0 LIMIT 1")
    AiRoutingConfigEntity findById(@Param("id") Long id);

    @Update("UPDATE ai_routing_config SET routing_mode = #{routingMode}, pinned_provider_code = #{pinnedProviderCode}, "
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt} WHERE id = #{id} AND is_deleted = 0")
    int updateConfig(AiRoutingConfigEntity entity);
}
