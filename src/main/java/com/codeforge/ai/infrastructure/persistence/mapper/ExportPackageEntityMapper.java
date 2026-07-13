package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExportPackageEntityMapper extends BaseMapper<ExportPackageEntity> {

    @Select("""
            SELECT id, app_id, app_version_id, package_type, storage_path, status,
                   created_by, updated_by, created_at, updated_at, is_deleted
            FROM export_package
            WHERE app_id = #{appId}
              AND is_deleted = 0
            ORDER BY created_at DESC, id DESC
            """)
    List<ExportPackageEntity> findByAppId(@Param("appId") Long appId);

    @Select("""
            <script>
            SELECT ep.app_version_id AS appVersionId, ep.status AS status
            FROM export_package ep
            INNER JOIN (
                SELECT app_version_id, MAX(id) AS max_id
                FROM export_package
                WHERE is_deleted = 0
                  AND app_version_id IN
                  <foreach collection="versionIds" item="versionId" open="(" separator="," close=")">
                      #{versionId}
                  </foreach>
                GROUP BY app_version_id
            ) latest ON ep.id = latest.max_id
            </script>
            """)
    List<com.codeforge.ai.infrastructure.persistence.projection.VersionExportStatusRow> findLatestStatusByVersionIds(
            @Param("versionIds") List<Long> versionIds);

    @Select("""
            SELECT id, app_id, app_version_id, package_type, storage_path, status,
                   created_by, updated_by, created_at, updated_at, is_deleted
            FROM export_package
            WHERE app_version_id = #{appVersionId}
              AND status = 'READY'
              AND is_deleted = 0
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    ExportPackageEntity findLatestReadyByAppVersionId(@Param("appVersionId") Long appVersionId);
}
