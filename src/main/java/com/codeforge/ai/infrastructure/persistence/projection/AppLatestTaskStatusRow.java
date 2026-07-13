package com.codeforge.ai.infrastructure.persistence.projection;

import lombok.Data;

@Data
public class AppLatestTaskStatusRow {
    private Long appId;
    private String taskStatus;
}
