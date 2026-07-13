package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.mybatisflex.core.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PromptTemplateVersionEntityMapper extends BaseMapper<PromptTemplateVersionEntity> {

    @Insert("""
            INSERT INTO prompt_template_version (
                template_id, version_no, system_prompt, user_prompt, variables_json, model_strategy_json,
                status, created_by, updated_by, created_at, updated_at, is_deleted
            ) VALUES (
                #{templateId}, #{versionNo}, #{systemPrompt}, #{userPrompt}, #{variablesJson}, #{modelStrategyJson},
                #{status}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertVersion(PromptTemplateVersionEntity entity);

    @Select("""
            SELECT id, template_id, version_no, system_prompt, user_prompt, variables_json, model_strategy_json,
                   status, published_by, published_at, created_by, updated_by, created_at, updated_at, is_deleted
            FROM prompt_template_version
            WHERE template_id = #{templateId}
              AND version_no = #{versionNo}
              AND is_deleted = 0
            LIMIT 1
            """)
    PromptTemplateVersionEntity findByTemplateIdAndVersionNo(@Param("templateId") Long templateId,
                                                             @Param("versionNo") Integer versionNo);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM prompt_template_version
            WHERE template_id = #{templateId}
              AND is_deleted = 0
            """)
    Integer findMaxVersionNo(@Param("templateId") Long templateId);

    @Update("""
            UPDATE prompt_template_version
            SET status = 'PUBLISHED',
                published_by = COALESCE(published_by, #{publishedBy}),
                published_at = COALESCE(published_at, #{publishedAt}),
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int markPublished(@Param("id") Long id,
                      @Param("publishedBy") Long publishedBy,
                      @Param("publishedAt") LocalDateTime publishedAt,
                      @Param("updatedBy") Long updatedBy);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM prompt_template_version
            WHERE template_id = #{templateId}
              AND is_deleted = 0
              AND (status = 'PUBLISHED' OR published_at IS NOT NULL)
            """)
    Integer findMaxEffectivelyPublishedVersionNo(@Param("templateId") Long templateId);

    @Select("""
            <script>
            SELECT id, template_id, version_no, system_prompt, user_prompt, variables_json, model_strategy_json,
                   status, published_by, published_at, created_by, updated_by, created_at, updated_at, is_deleted
            FROM prompt_template_version
            WHERE is_deleted = 0
              AND (status = 'PUBLISHED' OR published_at IS NOT NULL)
              <if test="templateIds != null and templateIds.size() > 0">
                AND template_id IN
                <foreach collection="templateIds" item="templateId" open="(" separator="," close=")">
                    #{templateId}
                </foreach>
              </if>
            </script>
            """)
    List<PromptTemplateVersionEntity> findPublishedVersionsByTemplateIds(@Param("templateIds") List<Long> templateIds);

    @Select("""
            SELECT id, template_id, version_no, system_prompt, user_prompt, variables_json, model_strategy_json,
                   status, published_by, published_at, created_by, updated_by, created_at, updated_at, is_deleted
            FROM prompt_template_version
            WHERE template_id = #{templateId}
              AND is_deleted = 0
            ORDER BY version_no DESC
            """)
    List<PromptTemplateVersionEntity> findByTemplateId(@Param("templateId") Long templateId);

    @Select("""
            SELECT COUNT(1)
            FROM prompt_template_version
            WHERE template_id = #{templateId}
              AND is_deleted = 0
            """)
    int countByTemplateId(@Param("templateId") Long templateId);

    @Update("""
            UPDATE prompt_template_version
            SET system_prompt = #{systemPrompt},
                user_prompt = #{userPrompt},
                variables_json = #{variablesJson},
                model_strategy_json = #{modelStrategyJson},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
              AND status = 'DRAFT'
              AND published_at IS NULL
            """)
    int updateDraftVersion(PromptTemplateVersionEntity entity);
}
