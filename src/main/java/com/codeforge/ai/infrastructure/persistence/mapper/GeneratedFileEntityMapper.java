package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GeneratedFileEntityMapper extends BaseMapper<GeneratedFileEntity> {

    @Select("SELECT id, app_version_id AS appVersionId, file_path AS filePath, "
            + "file_name AS fileName, file_type AS fileType, storage_path AS storagePath, "
            + "content_hash AS contentHash, file_size AS fileSize, file_content AS fileContent, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM generated_file WHERE app_version_id = #{appVersionId} AND is_deleted = 0 ORDER BY id ASC")
    List<GeneratedFileEntity> findByAppVersionId(@Param("appVersionId") Long appVersionId);

    @Select("SELECT id, app_version_id AS appVersionId, file_path AS filePath, "
            + "file_name AS fileName, file_type AS fileType, storage_path AS storagePath, "
            + "content_hash AS contentHash, file_size AS fileSize, file_content AS fileContent, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM generated_file WHERE app_version_id = #{appVersionId} AND file_path = #{filePath} AND is_deleted = 0 LIMIT 1")
    GeneratedFileEntity findByAppVersionIdAndFilePath(@Param("appVersionId") Long appVersionId,
                                                       @Param("filePath") String filePath);

    @Insert("INSERT INTO generated_file (app_version_id, file_path, file_name, file_type, storage_path, content_hash, "
            + "file_size, file_content, created_by, updated_by, created_at, updated_at, is_deleted) "
            + "VALUES (#{appVersionId}, #{filePath}, #{fileName}, #{fileType}, #{storagePath}, #{contentHash}, "
            + "#{fileSize}, #{fileContent}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertFile(GeneratedFileEntity entity);

    @Select("""
            <script>
            SELECT app_version_id AS appVersionId, COUNT(*) AS fileCount
            FROM generated_file
            WHERE is_deleted = 0
              AND app_version_id IN
              <foreach collection="versionIds" item="versionId" open="(" separator="," close=")">
                  #{versionId}
              </foreach>
            GROUP BY app_version_id
            </script>
            """)
    List<com.codeforge.ai.infrastructure.persistence.projection.VersionFileCountRow> countByVersionIds(
            @Param("versionIds") List<Long> versionIds);
}
