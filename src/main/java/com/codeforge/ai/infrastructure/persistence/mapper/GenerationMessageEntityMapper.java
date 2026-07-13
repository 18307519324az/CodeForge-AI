package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.generation.entity.GenerationMessageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GenerationMessageEntityMapper {

    @Insert("INSERT INTO generation_message (workspace_id, app_id, task_id, user_id, message_role, message_content, message_type, created_at, is_deleted) "
            + "VALUES (#{workspaceId}, #{appId}, #{taskId}, #{userId}, #{messageRole}, #{messageContent}, #{messageType}, #{createdAt}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(GenerationMessageEntity entity);

    @Select("SELECT id, workspace_id AS workspaceId, app_id AS appId, task_id AS taskId, user_id AS userId, "
            + "message_role AS messageRole, message_content AS messageContent, message_type AS messageType, "
            + "created_at AS createdAt, is_deleted AS isDeleted "
            + "FROM generation_message WHERE app_id = #{appId} AND is_deleted = 0 ORDER BY created_at ASC")
    List<GenerationMessageEntity> findByAppId(@Param("appId") Long appId);

    @Select("SELECT id, workspace_id AS workspaceId, app_id AS appId, task_id AS taskId, user_id AS userId, "
            + "message_role AS messageRole, message_content AS messageContent, message_type AS messageType, "
            + "created_at AS createdAt, is_deleted AS isDeleted "
            + "FROM generation_message WHERE app_id = #{appId} AND is_deleted = 0 ORDER BY id DESC LIMIT #{limit}")
    List<GenerationMessageEntity> findByAppIdWithLimit(@Param("appId") Long appId, @Param("limit") int limit);

    @Select("SELECT id, workspace_id AS workspaceId, app_id AS appId, task_id AS taskId, user_id AS userId, "
            + "message_role AS messageRole, message_content AS messageContent, message_type AS messageType, "
            + "created_at AS createdAt, is_deleted AS isDeleted "
            + "FROM generation_message WHERE app_id = #{appId} AND is_deleted = 0 AND id < #{cursor} ORDER BY id DESC LIMIT #{limit}")
    List<GenerationMessageEntity> findByAppIdBeforeId(@Param("appId") Long appId, @Param("cursor") Long cursor, @Param("limit") int limit);

    @Select("SELECT id, workspace_id AS workspaceId, app_id AS appId, task_id AS taskId, user_id AS userId, "
            + "message_role AS messageRole, message_content AS messageContent, message_type AS messageType, "
            + "created_at AS createdAt, is_deleted AS isDeleted "
            + "FROM generation_message WHERE task_id = #{taskId} AND is_deleted = 0 ORDER BY created_at ASC")
    List<GenerationMessageEntity> findByTaskId(@Param("taskId") Long taskId);
}
