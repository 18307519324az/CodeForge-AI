package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GenerationTaskEventEntityMapper extends BaseMapper<GenerationTaskEventEntity> {

    @Select("SELECT id, task_id AS taskId, event_type AS eventType, event_message AS eventMessage, "
            + "event_payload_json AS eventPayloadJson, request_id AS requestId, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM generation_task_event WHERE task_id = #{taskId} AND is_deleted = 0 ORDER BY id ASC")
    List<GenerationTaskEventEntity> findByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT id, task_id AS taskId, event_type AS eventType, event_message AS eventMessage, "
            + "event_payload_json AS eventPayloadJson, request_id AS requestId, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM generation_task_event WHERE task_id = #{taskId} AND is_deleted = 0 "
            + "AND id > #{afterEventId} ORDER BY id ASC")
    List<GenerationTaskEventEntity> findByTaskIdAfterId(@Param("taskId") Long taskId,
                                                        @Param("afterEventId") Long afterEventId);

    @Insert("INSERT INTO generation_task_event (task_id, event_type, event_message, event_payload_json, request_id, "
            + "created_by, updated_by, created_at, updated_at, is_deleted) "
            + "VALUES (#{taskId}, #{eventType}, #{eventMessage}, #{eventPayloadJson}, #{requestId}, "
            + "#{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEvent(GenerationTaskEventEntity entity);
}
