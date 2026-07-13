package com.codeforge.ai.domain.task.entity;

import com.codeforge.ai.domain.common.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.time.LocalDateTime;
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
@Table("generation_task")
public class GenerationTaskEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long workspaceId;
    private Long appId;
    private String taskType;
    private String taskStatus;
    private String idempotencyKey;
    private Long retryOfTaskId;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String requirement;
    private String requestPayloadJson;
    private Long promptTemplateId;
    private Long promptTemplateVersionId;
    private String resultSummaryJson;
    private String requestId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
