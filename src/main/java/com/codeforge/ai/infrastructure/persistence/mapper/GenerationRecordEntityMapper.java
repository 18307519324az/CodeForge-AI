package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.task.entity.GenerationRecordEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GenerationRecordEntityMapper extends BaseMapper<GenerationRecordEntity> {

    @Update("""
            UPDATE generation_record
            SET status = #{status},
                output_summary = #{outputSummary},
                duration_ms = #{durationMs},
                updated_by = #{updatedBy}
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateResultByTaskId(@Param("taskId") Long taskId,
                             @Param("status") String status,
                             @Param("outputSummary") String outputSummary,
                             @Param("durationMs") Long durationMs,
                             @Param("updatedBy") Long updatedBy);

    default int updateResultByTaskId(Long taskId,
                                     String outputSummary,
                                     Long durationMs,
                                     Long updatedBy) {
        return updateResultByTaskId(taskId, "SUCCESS", outputSummary, durationMs, updatedBy);
    }

    @Update("""
            UPDATE generation_record
            SET model_provider_id = #{modelProviderId},
                model_name = #{modelName},
                output_summary = #{outputSummary},
                token_input = #{tokenInput},
                token_output = #{tokenOutput},
                duration_ms = #{durationMs},
                updated_by = #{updatedBy}
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            """)
    int updateExecutionResultByTaskId(@Param("taskId") Long taskId,
                                      @Param("modelProviderId") Long modelProviderId,
                                      @Param("modelName") String modelName,
                                      @Param("outputSummary") String outputSummary,
                                      @Param("tokenInput") Integer tokenInput,
                                      @Param("tokenOutput") Integer tokenOutput,
                                      @Param("durationMs") Long durationMs,
                                      @Param("updatedBy") Long updatedBy);

    @Select("""
            SELECT id, workspace_id AS workspaceId, app_id AS appId, task_id AS taskId,
                   status,
                   prompt_template_version_id AS promptTemplateVersionId,
                   model_provider_id AS modelProviderId, model_name AS modelName,
                   input_summary AS inputSummary, output_summary AS outputSummary,
                   token_input AS tokenInput, token_output AS tokenOutput, duration_ms AS durationMs,
                   created_by AS createdBy, updated_by AS updatedBy,
                   created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted
            FROM generation_record
            WHERE task_id = #{taskId}
              AND is_deleted = 0
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    GenerationRecordEntity findLatestByTaskId(@Param("taskId") Long taskId);

    @Select("""
            SELECT id, workspace_id AS workspaceId, app_id AS appId, task_id AS taskId,
                   status,
                   prompt_template_version_id AS promptTemplateVersionId,
                   model_provider_id AS modelProviderId, model_name AS modelName,
                   input_summary AS inputSummary, output_summary AS outputSummary,
                   token_input AS tokenInput, token_output AS tokenOutput, duration_ms AS durationMs,
                   created_by AS createdBy, updated_by AS updatedBy,
                   created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted
            FROM generation_record
            WHERE app_id = #{appId}
              AND is_deleted = 0
            ORDER BY created_at DESC, id DESC
            """)
    List<GenerationRecordEntity> findByAppId(@Param("appId") Long appId);

    @Insert("INSERT INTO generation_record (workspace_id, app_id, task_id, status, prompt_template_version_id, model_provider_id, model_name, input_summary, output_summary, token_input, token_output, duration_ms, created_by, updated_by, created_at, updated_at, is_deleted) VALUES (#{workspaceId}, #{appId}, #{taskId}, #{status}, #{promptTemplateVersionId}, #{modelProviderId}, #{modelName}, #{inputSummary}, #{outputSummary}, #{tokenInput}, #{tokenOutput}, #{durationMs}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRecord(GenerationRecordEntity entity);

    @Select("""
            SELECT COUNT(1)
            FROM generation_record
            WHERE prompt_template_version_id = #{versionId}
              AND is_deleted = 0
            """)
    int countByPromptTemplateVersionId(@Param("versionId") Long versionId);

    @Select("""
            SELECT COUNT(1)
            FROM generation_record gr
            INNER JOIN prompt_template_version v ON gr.prompt_template_version_id = v.id
            WHERE v.template_id = #{templateId}
              AND gr.is_deleted = 0
              AND v.is_deleted = 0
            """)
    int countByTemplateId(@Param("templateId") Long templateId);
}
