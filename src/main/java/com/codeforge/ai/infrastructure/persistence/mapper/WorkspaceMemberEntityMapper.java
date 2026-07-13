package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkspaceMemberEntityMapper extends BaseMapper<WorkspaceMemberEntity> {

    @Insert("""
            INSERT INTO workspace_member (
                workspace_id, user_id, member_role, member_status, invited_by, joined_at,
                created_by, updated_by, created_at, updated_at, is_deleted
            ) VALUES (
                #{workspaceId}, #{userId}, #{memberRole}, #{memberStatus}, #{invitedBy}, #{joinedAt},
                #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWorkspaceMember(WorkspaceMemberEntity workspaceMemberEntity);

    @Select("""
            SELECT id,
                   workspace_id AS workspaceId,
                   user_id AS userId,
                   member_role AS memberRole,
                   member_status AS memberStatus,
                   invited_by AS invitedBy,
                   joined_at AS joinedAt,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM workspace_member
            WHERE workspace_id = #{workspaceId}
              AND user_id = #{userId}
              AND is_deleted = 0
            LIMIT 1
            """)
    WorkspaceMemberEntity findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    @Select("""
            SELECT id,
                   workspace_id AS workspaceId,
                   user_id AS userId,
                   member_role AS memberRole,
                   member_status AS memberStatus,
                   invited_by AS invitedBy,
                   joined_at AS joinedAt,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM workspace_member
            WHERE workspace_id = #{workspaceId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    WorkspaceMemberEntity findAnyByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    @Select("""
            SELECT id,
                   workspace_id AS workspaceId,
                   user_id AS userId,
                   member_role AS memberRole,
                   member_status AS memberStatus,
                   invited_by AS invitedBy,
                   joined_at AS joinedAt,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM workspace_member
            WHERE workspace_id = #{workspaceId}
              AND is_deleted = 0
            ORDER BY created_at ASC, id ASC
            """)
    List<WorkspaceMemberEntity> findByWorkspaceId(Long workspaceId);

    @Select("""
            SELECT id,
                   workspace_id AS workspaceId,
                   user_id AS userId,
                   member_role AS memberRole,
                   member_status AS memberStatus,
                   invited_by AS invitedBy,
                   joined_at AS joinedAt,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM workspace_member
            WHERE user_id = #{userId}
              AND is_deleted = 0
            ORDER BY created_at DESC
            """)
    List<WorkspaceMemberEntity> findByUserId(Long userId);

    @Select("""
            SELECT id,
                   workspace_id AS workspaceId,
                   user_id AS userId,
                   member_role AS memberRole,
                   member_status AS memberStatus,
                   invited_by AS invitedBy,
                   joined_at AS joinedAt,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM workspace_member
            WHERE id = #{memberId}
              AND workspace_id = #{workspaceId}
              AND is_deleted = 0
            LIMIT 1
            """)
    WorkspaceMemberEntity findByIdAndWorkspaceId(Long memberId, Long workspaceId);

    @Update("""
            UPDATE workspace_member
            SET member_role = #{memberRole},
                member_status = #{memberStatus},
                invited_by = #{invitedBy},
                joined_at = #{joinedAt},
                updated_by = #{updatedBy}
            WHERE id = #{memberId}
              AND is_deleted = 0
            """)
    int updateMembership(Long memberId, String memberRole, String memberStatus, Long invitedBy,
                         java.time.LocalDateTime joinedAt, Long updatedBy);

    @Update("""
            UPDATE workspace_member
            SET member_role = #{memberRole},
                member_status = #{memberStatus},
                invited_by = #{invitedBy},
                joined_at = #{joinedAt},
                updated_by = #{updatedBy},
                is_deleted = 0
            WHERE id = #{memberId}
            """)
    int restoreMembership(Long memberId, String memberRole, String memberStatus, Long invitedBy,
                          java.time.LocalDateTime joinedAt, Long updatedBy);
}
