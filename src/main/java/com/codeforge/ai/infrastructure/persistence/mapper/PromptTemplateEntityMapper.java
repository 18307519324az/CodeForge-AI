package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PromptTemplateEntityMapper extends BaseMapper<PromptTemplateEntity> {

    @Insert("""
            INSERT INTO prompt_template (
                workspace_id, template_name, template_scene, status, current_version_no, remark,
                created_by, updated_by, created_at, updated_at, is_deleted
            ) VALUES (
                #{workspaceId}, #{templateName}, #{templateScene}, #{status}, #{currentVersionNo}, #{remark},
                #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTemplate(PromptTemplateEntity entity);

    @Select("""
            SELECT id,
                   workspace_id AS workspaceId,
                   template_name AS templateName,
                   template_scene AS templateScene,
                   status,
                   current_version_no AS currentVersionNo,
                   remark,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM prompt_template
            WHERE workspace_id = #{workspaceId}
              AND template_name = #{templateName}
              AND is_deleted = 0
            LIMIT 1
            """)
    PromptTemplateEntity findByWorkspaceIdAndTemplateName(@Param("workspaceId") Long workspaceId,
                                                          @Param("templateName") String templateName);

    @Select("""
            SELECT id,
                   workspace_id AS workspaceId,
                   template_name AS templateName,
                   template_scene AS templateScene,
                   status,
                   current_version_no AS currentVersionNo,
                   remark,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM prompt_template
            WHERE workspace_id = #{workspaceId}
              AND template_name = #{templateName}
              AND id != #{templateId}
              AND is_deleted = 0
            LIMIT 1
            """)
    PromptTemplateEntity findByWorkspaceIdAndTemplateNameExcludingId(@Param("workspaceId") Long workspaceId,
                                                                     @Param("templateName") String templateName,
                                                                     @Param("templateId") Long templateId);

    @Select("""
            <script>
            SELECT id,
                   workspace_id AS workspaceId,
                   template_name AS templateName,
                   template_scene AS templateScene,
                   status,
                   current_version_no AS currentVersionNo,
                   remark,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   is_deleted AS isDeleted
            FROM prompt_template
            WHERE is_deleted = 0
              <if test="workspaceIds != null and workspaceIds.size() > 0">
                AND workspace_id IN
                <foreach collection="workspaceIds" item="workspaceId" open="(" separator="," close=")">
                    #{workspaceId}
                </foreach>
              </if>
              <if test="keyword != null and keyword != ''">
                AND template_name LIKE CONCAT('%', #{keyword}, '%')
              </if>
              <if test="templateScene != null and templateScene != ''">
                AND template_scene = #{templateScene}
              </if>
              <if test="status != null and status != ''">
                AND status = #{status}
              </if>
            ORDER BY updated_at DESC, id DESC
            </script>
            """)
    List<PromptTemplateEntity> findAccessibleTemplates(@Param("workspaceIds") List<Long> workspaceIds,
                                                       @Param("keyword") String keyword,
                                                       @Param("templateScene") String templateScene,
                                                       @Param("status") String status);

    @Update("""
            UPDATE prompt_template
            SET template_name = #{templateName},
                template_scene = #{templateScene},
                remark = #{remark},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateTemplate(PromptTemplateEntity entity);

    @Update("""
            UPDATE prompt_template
            SET status = #{status},
                current_version_no = #{currentVersionNo},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updatePublishedVersion(@Param("id") Long id,
                               @Param("status") String status,
                               @Param("currentVersionNo") Integer currentVersionNo,
                               @Param("updatedBy") Long updatedBy);

    @Update("""
            UPDATE prompt_template
            SET status = #{status},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("updatedBy") Long updatedBy);
}
