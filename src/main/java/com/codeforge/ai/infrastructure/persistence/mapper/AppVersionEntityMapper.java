package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppVersionEntityMapper extends BaseMapper<AppVersionEntity> {

    @Select("""
            SELECT COALESCE(MAX(version_no), 0) AS maxVersion
            FROM app_version
            WHERE app_id = #{appId}
              AND is_deleted = 0
            """)
    Integer findMaxVersionNo(@Param("appId") Long appId);

    @Select("SELECT id, app_id AS appId, version_no AS versionNo, version_source AS versionSource, "
            + "source_task_id AS sourceTaskId, change_summary AS changeSummary, status, "
            + "published_at AS publishedAt, preview_url AS previewUrl, preview_status AS previewStatus, "
            + "build_status AS buildStatus, build_log AS buildLog, built_at AS builtAt, "
            + "created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM app_version WHERE app_id = #{appId} AND is_deleted = 0 ORDER BY version_no DESC")
    List<AppVersionEntity> findByAppId(@Param("appId") Long appId);

    @Select("SELECT id, app_id AS appId, version_no AS versionNo, version_source AS versionSource, "
            + "source_task_id AS sourceTaskId, change_summary AS changeSummary, status, "
            + "published_at AS publishedAt, preview_url AS previewUrl, preview_status AS previewStatus, "
            + "build_status AS buildStatus, build_log AS buildLog, built_at AS builtAt, "
            + "created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM app_version WHERE app_id = #{appId} AND id = #{versionId} AND is_deleted = 0 LIMIT 1")
    AppVersionEntity findByAppIdAndVersionId(@Param("appId") Long appId, @Param("versionId") Long versionId);

    @Select("SELECT id, app_id AS appId, version_no AS versionNo, version_source AS versionSource, "
            + "source_task_id AS sourceTaskId, change_summary AS changeSummary, status, "
            + "published_at AS publishedAt, preview_url AS previewUrl, preview_status AS previewStatus, "
            + "build_status AS buildStatus, build_log AS buildLog, built_at AS builtAt, "
            + "created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM app_version WHERE source_task_id = #{taskId} AND is_deleted = 0 ORDER BY id ASC")
    List<AppVersionEntity> findBySourceTaskId(@Param("taskId") Long taskId);

    @Insert("INSERT INTO app_version (app_id, version_no, version_source, source_task_id, change_summary, status, published_at, created_by, updated_by, created_at, updated_at, is_deleted) VALUES (#{appId}, #{versionNo}, #{versionSource}, #{sourceTaskId}, #{changeSummary}, #{status}, #{publishedAt}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertVersion(AppVersionEntity entity);

    @Select("SELECT id, app_id AS appId, version_no AS versionNo, version_source AS versionSource, "
            + "source_task_id AS sourceTaskId, change_summary AS changeSummary, status, "
            + "published_at AS publishedAt, preview_url AS previewUrl, preview_status AS previewStatus, "
            + "build_status AS buildStatus, build_log AS buildLog, built_at AS builtAt, "
            + "created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM app_version WHERE id = #{id} AND is_deleted = 0 LIMIT 1")
    AppVersionEntity findById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, app_id AS appId, version_no AS versionNo, version_source AS versionSource,
                   source_task_id AS sourceTaskId, change_summary AS changeSummary, status,
                   published_at AS publishedAt, preview_url AS previewUrl, preview_status AS previewStatus,
                   build_status AS buildStatus, build_log AS buildLog, built_at AS builtAt,
                   created_by AS createdBy, updated_by AS updatedBy,
                   created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted
            FROM app_version
            WHERE is_deleted = 0
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    List<AppVersionEntity> findByIds(@Param("ids") List<Long> ids);

    @Update("UPDATE app_version SET build_status = #{buildStatus}, build_log = #{buildLog}, "
            + "updated_by = #{updatedBy} WHERE id = #{id} AND is_deleted = 0")
    int updateBuildStatus(@Param("id") Long id, @Param("buildStatus") String buildStatus,
                          @Param("buildLog") String buildLog, @Param("updatedBy") Long updatedBy);

    @Update("UPDATE app_version SET preview_url = #{previewUrl}, preview_status = #{previewStatus}, "
            + "updated_by = #{updatedBy} WHERE id = #{id} AND is_deleted = 0")
    int updatePreviewInfo(@Param("id") Long id, @Param("previewUrl") String previewUrl,
                          @Param("previewStatus") String previewStatus,
                          @Param("updatedBy") Long updatedBy);
}
