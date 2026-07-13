package com.codeforge.ai.infrastructure.persistence.projection;

import lombok.Data;

@Data
public class VersionExportStatusRow {
    private Long appVersionId;
    private String status;
}
