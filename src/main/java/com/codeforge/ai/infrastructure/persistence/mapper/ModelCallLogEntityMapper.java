package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.application.dto.admin.ModelCallOverviewMetrics;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.mybatisflex.core.BaseMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ModelCallLogEntityMapper extends BaseMapper<ModelCallLogEntity> {

    // Admin page: with JOIN to model_provider for providerCode and apiProtocol
    @Select("<script>SELECT l.id, l.task_id AS taskId, l.app_id AS appId, l.session_id AS sessionId, "
            + "l.provider_id AS providerId, l.model_name AS modelName, l.request_id AS requestId, "
            + "l.status, l.input_tokens AS inputTokens, l.output_tokens AS outputTokens, "
            + "l.duration_ms AS durationMs, l.fallback_used AS fallbackUsed, l.generation_source AS generationSource, "
            + "l.prompt_template_version_id AS promptTemplateVersionId, "
            + "l.prompt_template_code AS promptTemplateCode, l.prompt_template_version_no AS promptTemplateVersionNo, "
            + "l.system_prompt_sha256 AS systemPromptSha256, l.user_prompt_sha256 AS userPromptSha256, "
            + "l.combined_prompt_fingerprint AS combinedPromptFingerprint, "
            + "l.error_message AS errorMessage, l.created_at AS createdAt, l.created_by AS createdBy, "
            + "COALESCE(l.provider_code, p.provider_code) AS providerCode, "
            + "COALESCE(l.api_protocol, p.api_protocol) AS apiProtocol "
            + "FROM model_call_log l "
            + "LEFT JOIN model_provider p ON l.provider_id = p.id "
            + "<if test=\"keywordPattern != null and keywordPattern != ''\">"
            + "WHERE (l.model_name LIKE #{keywordPattern} OR l.request_id LIKE #{keywordPattern} OR l.status LIKE #{keywordPattern}"
            + " OR COALESCE(l.provider_code, p.provider_code) LIKE #{keywordPattern})"
            + "</if>ORDER BY l.created_at DESC, l.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<ModelCallLogEntity> findPage(@Param("offset") long offset, @Param("limit") long limit,
                                       @Param("keywordPattern") String keywordPattern);

    @Select("<script>SELECT COUNT(1) FROM model_call_log l "
            + "LEFT JOIN model_provider p ON l.provider_id = p.id "
            + "<if test=\"keywordPattern != null and keywordPattern != ''\">"
            + "WHERE (l.model_name LIKE #{keywordPattern} OR l.request_id LIKE #{keywordPattern} OR l.status LIKE #{keywordPattern}"
            + " OR COALESCE(l.provider_code, p.provider_code) LIKE #{keywordPattern})"
            + "</if></script>")
    long countByKeyword(@Param("keywordPattern") String keywordPattern);

    @Select("SELECT COUNT(1) FROM model_call_log")
    long countAllLogs();

    @Select("SELECT COUNT(1) FROM model_call_log WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT "
            + "l.id, l.task_id AS taskId, l.app_id AS appId, l.session_id AS sessionId, "
            + "l.provider_id AS providerId, l.model_name AS modelName, l.request_id AS requestId, "
            + "l.status, l.input_tokens AS inputTokens, l.output_tokens AS outputTokens, "
            + "l.duration_ms AS durationMs, l.fallback_used AS fallbackUsed, l.generation_source AS generationSource, "
            + "l.prompt_template_version_id AS promptTemplateVersionId, "
            + "l.prompt_template_code AS promptTemplateCode, l.prompt_template_version_no AS promptTemplateVersionNo, "
            + "l.system_prompt_sha256 AS systemPromptSha256, l.user_prompt_sha256 AS userPromptSha256, "
            + "l.combined_prompt_fingerprint AS combinedPromptFingerprint, "
            + "l.error_message AS errorMessage, l.created_at AS createdAt, "
            + "COALESCE(l.provider_code, p.provider_code) AS providerCode, "
            + "COALESCE(l.api_protocol, p.api_protocol) AS apiProtocol "
            + "FROM model_call_log l "
            + "LEFT JOIN model_provider p ON l.provider_id = p.id "
            + "ORDER BY l.created_at DESC LIMIT #{limit}")
    List<ModelCallLogEntity> findRecentLogs(@Param("limit") long limit);

    @Insert("INSERT INTO model_call_log (task_id, app_id, session_id, provider_id, provider_code, model_name, "
            + "api_protocol, request_id, status, input_tokens, output_tokens, duration_ms, fallback_used, generation_source, "
            + "prompt_template_version_id, prompt_template_code, prompt_template_version_no, "
            + "system_prompt_sha256, user_prompt_sha256, combined_prompt_fingerprint, "
            + "error_message, created_by, created_at) "
            + "VALUES (#{taskId}, #{appId}, #{sessionId}, #{providerId}, #{providerCode}, #{modelName}, "
            + "#{apiProtocol}, #{requestId}, #{status}, #{inputTokens}, #{outputTokens}, #{durationMs}, "
            + "#{fallbackUsed}, #{generationSource}, #{promptTemplateVersionId}, #{promptTemplateCode}, "
            + "#{promptTemplateVersionNo}, #{systemPromptSha256}, #{userPromptSha256}, #{combinedPromptFingerprint}, "
            + "#{errorMessage}, #{createdBy}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCallLog(ModelCallLogEntity entity);

    @Select("SELECT l.id, l.task_id AS taskId, l.app_id AS appId, l.session_id AS sessionId, "
            + "l.provider_id AS providerId, l.model_name AS modelName, l.request_id AS requestId, "
            + "l.status, l.input_tokens AS inputTokens, l.output_tokens AS outputTokens, "
            + "l.duration_ms AS durationMs, l.fallback_used AS fallbackUsed, l.generation_source AS generationSource, "
            + "l.prompt_template_version_id AS promptTemplateVersionId, "
            + "l.prompt_template_code AS promptTemplateCode, l.prompt_template_version_no AS promptTemplateVersionNo, "
            + "l.system_prompt_sha256 AS systemPromptSha256, l.user_prompt_sha256 AS userPromptSha256, "
            + "l.combined_prompt_fingerprint AS combinedPromptFingerprint, "
            + "l.error_message AS errorMessage, l.created_by AS createdBy, l.created_at AS createdAt, "
            + "COALESCE(l.provider_code, p.provider_code) AS providerCode, "
            + "COALESCE(l.api_protocol, p.api_protocol) AS apiProtocol "
            + "FROM model_call_log l "
            + "LEFT JOIN model_provider p ON l.provider_id = p.id "
            + "WHERE l.id = #{id}")
    ModelCallLogEntity findById(@Param("id") Long id);

    @Select("SELECT l.id, l.task_id AS taskId, l.app_id AS appId, l.session_id AS sessionId, "
            + "l.provider_id AS providerId, l.model_name AS modelName, l.request_id AS requestId, "
            + "l.status, l.input_tokens AS inputTokens, l.output_tokens AS outputTokens, "
            + "l.duration_ms AS durationMs, l.fallback_used AS fallbackUsed, l.generation_source AS generationSource, "
            + "l.prompt_template_version_id AS promptTemplateVersionId, "
            + "l.prompt_template_code AS promptTemplateCode, l.prompt_template_version_no AS promptTemplateVersionNo, "
            + "l.system_prompt_sha256 AS systemPromptSha256, l.user_prompt_sha256 AS userPromptSha256, "
            + "l.combined_prompt_fingerprint AS combinedPromptFingerprint, "
            + "l.error_message AS errorMessage, l.created_by AS createdBy, l.created_at AS createdAt, "
            + "COALESCE(l.provider_code, p.provider_code) AS providerCode, "
            + "COALESCE(l.api_protocol, p.api_protocol) AS apiProtocol "
            + "FROM model_call_log l "
            + "LEFT JOIN model_provider p ON l.provider_id = p.id "
            + "WHERE l.task_id = #{taskId} ORDER BY l.created_at DESC")
    List<ModelCallLogEntity> findByTaskId(@Param("taskId") Long taskId);

    @Select("""
            SELECT COUNT(1)
            FROM model_call_log
            WHERE prompt_template_version_id = #{versionId}
            """)
    int countByPromptTemplateVersionId(@Param("versionId") Long versionId);

    @Select("""
            SELECT COUNT(1) AS requestCount,
                   SUM(CASE WHEN status IN ('SUCCESS', 'FALLBACK') THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount,
                   COALESCE(SUM(COALESCE(input_tokens, 0)), 0) AS tokenInput,
                   COALESCE(SUM(COALESCE(output_tokens, 0)), 0) AS tokenOutput,
                   COALESCE(ROUND(AVG(COALESCE(duration_ms, 0))), 0) AS avgDurationMs,
                   MAX(created_at) AS dataAsOf
            FROM model_call_log
            WHERE status IN ('SUCCESS', 'FAILED', 'FALLBACK')
            """)
    ModelCallOverviewMetrics aggregateAllFinalizedCalls();

    @Select("""
            SELECT COUNT(1) AS requestCount,
                   SUM(CASE WHEN status IN ('SUCCESS', 'FALLBACK') THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount,
                   COALESCE(SUM(COALESCE(input_tokens, 0)), 0) AS tokenInput,
                   COALESCE(SUM(COALESCE(output_tokens, 0)), 0) AS tokenOutput,
                   COALESCE(ROUND(AVG(COALESCE(duration_ms, 0))), 0) AS avgDurationMs,
                   MAX(created_at) AS dataAsOf
            FROM model_call_log
            WHERE status IN ('SUCCESS', 'FAILED', 'FALLBACK')
              AND DATE(created_at) = #{statDate}
            """)
    ModelCallOverviewMetrics aggregateFinalizedCallsByDate(@Param("statDate") LocalDate statDate);

    @Select("""
            SELECT MAX(created_at)
            FROM model_call_log
            WHERE status IN ('SUCCESS', 'FAILED', 'FALLBACK')
            """)
    LocalDateTime findLatestFinalizedCallAt();

    @Select("""
            SELECT DISTINCT DATE(created_at)
            FROM model_call_log
            WHERE status IN ('SUCCESS', 'FAILED', 'FALLBACK')
            ORDER BY DATE(created_at) ASC
            """)
    List<LocalDate> findDistinctFinalizedCallDates();
}
