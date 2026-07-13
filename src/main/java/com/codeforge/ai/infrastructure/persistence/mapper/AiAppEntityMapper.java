package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiAppEntityMapper extends BaseMapper<AiAppEntity> {

    String AI_APP_SELECT_COLUMNS =
            "id, workspace_id AS workspaceId, name, description, cover_url AS coverUrl, app_type AS appType, "
                    + "status, visibility, current_version_id AS currentVersionId, latest_task_id AS latestTaskId, "
                    + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, updated_at AS updatedAt, "
                    + "is_deleted AS isDeleted";

    @Insert("""
            INSERT INTO ai_app (
                id, workspace_id, name, description, cover_url, app_type, status, visibility,
                current_version_id, latest_task_id, created_by, updated_by, created_at, updated_at, is_deleted
            ) VALUES (
                #{id}, #{workspaceId}, #{name}, #{description}, #{coverUrl}, #{appType}, #{status}, #{visibility},
                #{currentVersionId}, #{latestTaskId}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted}
            )
            """)
    int insertApp(AiAppEntity entity);

    @Select("<script>"
            + "SELECT " + AI_APP_SELECT_COLUMNS + " FROM ai_app "
            + "WHERE is_deleted = 0 "
            + "<if test='workspaceIds != null and workspaceIds.size() > 0'>"
            + "AND workspace_id IN "
            + "<foreach collection='workspaceIds' item='workspaceId' open='(' separator=',' close=')'>"
            + "#{workspaceId}"
            + "</foreach>"
            + "</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "<if test='status != null and status != \"\"'>"
            + "AND status = #{status}"
            + "</if>"
            + "<if test='appType != null and appType != \"\"'>"
            + "AND app_type = #{appType}"
            + "</if>"
            + "ORDER BY updated_at DESC, id DESC"
            + "</script>")
    List<AiAppEntity> findAccessibleApps(@Param("workspaceIds") List<Long> workspaceIds,
                                         @Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("appType") String appType);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_app
            WHERE is_deleted = 0
              <if test="workspaceIds != null and workspaceIds.size() > 0">
                AND workspace_id IN
                <foreach collection="workspaceIds" item="workspaceId" open="(" separator="," close=")">
                    #{workspaceId}
                </foreach>
              </if>
              <if test="keyword != null and keyword != ''">
                AND (name LIKE CONCAT('%', #{keyword}, '%')
                     OR description LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
              <if test="appType != null and appType != ''">
                AND app_type = #{appType}
              </if>
            </script>
            """)
    long countAccessibleApps(@Param("workspaceIds") List<Long> workspaceIds,
                             @Param("keyword") String keyword,
                             @Param("status") String status,
                             @Param("appType") String appType);

    @Select("<script>"
            + "SELECT " + AI_APP_SELECT_COLUMNS + " FROM ai_app "
            + "WHERE is_deleted = 0 "
            + "<if test='workspaceIds != null and workspaceIds.size() > 0'>"
            + "AND workspace_id IN "
            + "<foreach collection='workspaceIds' item='workspaceId' open='(' separator=',' close=')'>"
            + "#{workspaceId}"
            + "</foreach>"
            + "</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "<if test='status != null and status != \"\"'>"
            + "AND status = #{status}"
            + "</if>"
            + "<if test='appType != null and appType != \"\"'>"
            + "AND app_type = #{appType}"
            + "</if>"
            + "ORDER BY updated_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AiAppEntity> findAccessibleAppsPage(@Param("workspaceIds") List<Long> workspaceIds,
                                             @Param("keyword") String keyword,
                                             @Param("status") String status,
                                             @Param("appType") String appType,
                                             @Param("offset") long offset,
                                             @Param("limit") long limit);

    @Update("""
            <script>
            UPDATE ai_app
            <set>
                <if test="name != null">name = #{name},</if>
                <if test="description != null">description = #{description},</if>
                <if test="coverUrl != null">cover_url = #{coverUrl},</if>
                <if test="visibility != null">visibility = #{visibility},</if>
                updated_by = #{updatedBy}
            </set>
            WHERE id = #{id}
              AND is_deleted = 0
            </script>
            """)
    int updateApp(AiAppEntity entity);

    @Update("""
            UPDATE ai_app
            SET status = #{status},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedBy") Long updatedBy);

    @Update("""
            UPDATE ai_app
            SET is_deleted = 1,
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int softDelete(@Param("id") Long id, @Param("updatedBy") Long updatedBy);

    @Update("""
            UPDATE ai_app
            SET latest_task_id = #{latestTaskId},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateLatestTaskId(@Param("id") Long id,
                           @Param("latestTaskId") Long latestTaskId,
                           @Param("updatedBy") Long updatedBy);

    @Update("""
            UPDATE ai_app
            SET current_version_id = #{currentVersionId},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateCurrentVersionId(@Param("id") Long id,
                               @Param("currentVersionId") Long currentVersionId,
                               @Param("updatedBy") Long updatedBy);

    @Select("""
            SELECT COUNT(*)
            FROM ai_app
            WHERE is_deleted = 0
            """)
    long countAllApps();

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_app
            WHERE is_deleted = 0
              <if test="keyword != null and keyword != ''">
                AND (name LIKE #{keyword}
                     OR description LIKE #{keyword})
              </if>
            </script>
            """)
    long countAdminApps(@Param("keyword") String keyword);

    @Select("<script>"
            + "SELECT " + AI_APP_SELECT_COLUMNS + " FROM ai_app "
            + "WHERE is_deleted = 0 "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (name LIKE #{keyword} OR description LIKE #{keyword})"
            + "</if>"
            + "ORDER BY updated_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AiAppEntity> findAdminPage(@Param("offset") long offset,
                                    @Param("limit") long limit,
                                    @Param("keyword") String keyword);

    @Select("<script>"
            + "SELECT " + AI_APP_SELECT_COLUMNS + " FROM ai_app "
            + "WHERE is_deleted = 0 AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    List<AiAppEntity> findByIds(@Param("ids") List<Long> ids);

    @Select("SELECT " + AI_APP_SELECT_COLUMNS + " FROM ai_app WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    AiAppEntity selectForUpdateById(@Param("id") Long id);
}
