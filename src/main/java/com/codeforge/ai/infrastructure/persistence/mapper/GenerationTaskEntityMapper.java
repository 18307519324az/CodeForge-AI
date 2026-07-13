package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GenerationTaskEntityMapper extends BaseMapper<GenerationTaskEntity> {

    @Select("""
            SELECT id, workspace_id, app_id, task_type, requirement, task_status, idempotency_key,
                   retry_of_task_id, retry_count, next_retry_at, request_payload_json,
                   prompt_template_id, prompt_template_version_id,
                   result_summary_json,
                   request_id, error_code, error_message, queued_at, started_at, finished_at,
                   created_by, updated_by, created_at, updated_at, is_deleted
            FROM generation_task
            WHERE workspace_id = #{workspaceId}
              AND app_id = #{appId}
              AND idempotency_key = #{idempotencyKey}
              AND is_deleted = 0
            LIMIT 1
            """)
    GenerationTaskEntity findByIdempotencyKey(@Param("workspaceId") Long workspaceId,
                                              @Param("appId") Long appId,
                                              @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE generation_task
            SET task_status = 'CANCELLED',
                finished_at = #{finishedAt},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
              AND task_status IN ('QUEUED', 'RUNNING', 'GENERATING', 'PERSISTING')
            """)
    int cancelIfActive(@Param("id") Long id,
                       @Param("finishedAt") java.time.LocalDateTime finishedAt,
                       @Param("updatedBy") Long updatedBy);

    @Update("""
            UPDATE generation_task
            SET task_status = #{taskStatus},
                started_at = COALESCE(#{startedAt}, started_at),
                finished_at = COALESCE(#{finishedAt}, finished_at),
                error_code = #{errorCode},
                error_message = #{errorMessage},
                result_summary_json = #{resultSummaryJson},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
              AND task_status = #{expectedStatus}
            """)
    int transitionState(@Param("id") Long id,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("taskStatus") String taskStatus,
                        @Param("startedAt") java.time.LocalDateTime startedAt,
                        @Param("finishedAt") java.time.LocalDateTime finishedAt,
                        @Param("errorCode") String errorCode,
                        @Param("errorMessage") String errorMessage,
                        @Param("resultSummaryJson") String resultSummaryJson,
                        @Param("updatedBy") Long updatedBy);

    @Update("""
            UPDATE generation_task
            SET task_status = #{taskStatus},
                error_code = #{errorCode},
                error_message = #{errorMessage},
                finished_at = #{finishedAt},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND is_deleted = 0
              AND task_status IN ('QUEUED', 'RUNNING', 'GENERATING', 'PERSISTING')
            """)
    int updateTerminalState(@Param("id") Long id,
                            @Param("taskStatus") String taskStatus,
                            @Param("errorCode") String errorCode,
                            @Param("errorMessage") String errorMessage,
                            @Param("finishedAt") java.time.LocalDateTime finishedAt,
                            @Param("updatedBy") Long updatedBy);

    @Insert("INSERT INTO generation_task (workspace_id, app_id, task_type, task_status, idempotency_key, "
            + "retry_of_task_id, retry_count, next_retry_at, request_payload_json, "
            + "prompt_template_id, prompt_template_version_id, result_summary_json, "
            + "request_id, error_code, error_message, queued_at, started_at, finished_at, "
            + "created_by, updated_by, created_at, updated_at, is_deleted) "
            + "VALUES (#{workspaceId}, #{appId}, #{taskType}, #{taskStatus}, #{idempotencyKey}, "
            + "#{retryOfTaskId}, #{retryCount}, #{nextRetryAt}, #{requestPayloadJson}, "
            + "#{promptTemplateId}, #{promptTemplateVersionId}, #{resultSummaryJson}, "
            + "#{requestId}, #{errorCode}, #{errorMessage}, #{queuedAt}, #{startedAt}, #{finishedAt}, "
            + "#{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTask(GenerationTaskEntity entity);

    @Select("SELECT COUNT(1) FROM generation_task WHERE is_deleted = 0")
    long countAllTasks();

    @Select("SELECT COUNT(1) FROM generation_task WHERE task_status = #{status} AND is_deleted = 0")
    long countByStatus(@Param("status") String status);

    @Select("SELECT id FROM generation_task WHERE app_id = #{appId} AND task_status IN ('QUEUED','RUNNING','GENERATING','PERSISTING') AND is_deleted = 0 LIMIT 1")
    Long findRunningTaskId(@Param("appId") Long appId);

    @Select("""
            <script>
            SELECT DISTINCT app_id
            FROM generation_task
            WHERE is_deleted = 0
              AND task_status IN ('QUEUED', 'RUNNING', 'GENERATING', 'PERSISTING')
              AND app_id IN
              <foreach collection="appIds" item="appId" open="(" separator="," close=")">
                  #{appId}
              </foreach>
            </script>
            """)
    List<Long> findRunningAppIds(@Param("appIds") List<Long> appIds);

    @Select("""
            <script>
            SELECT gt.app_id AS appId, gt.task_status AS taskStatus
            FROM generation_task gt
            INNER JOIN (
                SELECT app_id, MAX(id) AS max_id
                FROM generation_task
                WHERE is_deleted = 0
                  AND app_id IN
                  <foreach collection="appIds" item="appId" open="(" separator="," close=")">
                      #{appId}
                  </foreach>
                GROUP BY app_id
            ) latest ON gt.id = latest.max_id
            </script>
            """)
    List<com.codeforge.ai.infrastructure.persistence.projection.AppLatestTaskStatusRow> findLatestTaskStatusByAppIds(
            @Param("appIds") List<Long> appIds);

    @Select("""
            SELECT id, workspace_id, app_id, task_type, requirement, task_status, idempotency_key,
                   retry_of_task_id, retry_count, next_retry_at, request_payload_json,
                   prompt_template_id, prompt_template_version_id,
                   result_summary_json,
                   request_id, error_code, error_message, queued_at, started_at, finished_at,
                   created_by, updated_by, created_at, updated_at, is_deleted
            FROM generation_task
            WHERE app_id = #{appId}
              AND is_deleted = 0
            ORDER BY created_at DESC, id DESC
            """)
    List<GenerationTaskEntity> findByAppId(@Param("appId") Long appId);
}
