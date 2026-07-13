package com.codeforge.ai.domain.app.entity;

import com.codeforge.ai.domain.common.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("artifact_snapshot")
public class ArtifactSnapshotEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long appVersionId;
    private String snapshotType;
    private String snapshotPath;
    private String snapshotContentJson;
    private String contentHash;
}
