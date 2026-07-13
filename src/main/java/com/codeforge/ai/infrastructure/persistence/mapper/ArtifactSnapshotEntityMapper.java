package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.app.entity.ArtifactSnapshotEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArtifactSnapshotEntityMapper extends BaseMapper<ArtifactSnapshotEntity> {

    @Select("""
            SELECT id, app_version_id, snapshot_type, snapshot_path, snapshot_content_json, content_hash,
                   created_by, updated_by, created_at, updated_at, is_deleted
            FROM artifact_snapshot
            WHERE app_version_id = #{appVersionId}
              AND snapshot_type = #{snapshotType}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    ArtifactSnapshotEntity findLatestByAppVersionIdAndSnapshotType(@Param("appVersionId") Long appVersionId,
                                                                   @Param("snapshotType") String snapshotType);
}
