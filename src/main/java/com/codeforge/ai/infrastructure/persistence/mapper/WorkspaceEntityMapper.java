package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkspaceEntityMapper extends BaseMapper<WorkspaceEntity> {

    @Insert("""
            INSERT INTO workspace (
                name, description, owner_user_id, status, plan_code,
                created_by, updated_by, created_at, updated_at, is_deleted
            ) VALUES (
                #{name}, #{description}, #{ownerUserId}, #{status}, #{planCode},
                #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWorkspace(WorkspaceEntity workspaceEntity);

    @Select("""
            SELECT id,
                   name,
                   description,
                   owner_user_id AS ownerUserId,
                   status,
                   plan_code AS planCode,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM workspace
            WHERE owner_user_id = #{ownerUserId}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    WorkspaceEntity findLatestByOwnerUserId(Long ownerUserId);

    @Select("""
            SELECT id,
                   name,
                   description,
                   owner_user_id AS ownerUserId,
                   status,
                   plan_code AS planCode,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM workspace
            WHERE owner_user_id = #{ownerUserId}
              AND name = #{name}
              AND is_deleted = 0
            LIMIT 1
            """)
    WorkspaceEntity findByOwnerUserIdAndName(Long ownerUserId, String name);

    @Select("""
            SELECT id,
                   name,
                   description,
                   owner_user_id AS ownerUserId,
                   status,
                   plan_code AS planCode,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM workspace
            WHERE owner_user_id = #{ownerUserId}
              AND name = #{name}
            LIMIT 1
            """)
    WorkspaceEntity findAnyByOwnerUserIdAndName(Long ownerUserId, String name);
}
