package com.codeforge.ai.infrastructure.persistence.projection;

import lombok.Data;

@Data
public class VersionFileCountRow {
    private Long appVersionId;
    private Long fileCount;
}
