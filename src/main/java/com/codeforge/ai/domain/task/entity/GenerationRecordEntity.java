package com.codeforge.ai.domain.task.entity;

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
@Table("generation_record")
public class GenerationRecordEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long workspaceId;
    private Long appId;
    private Long taskId;
    private String status;
    private Long promptTemplateVersionId;
    private Long modelProviderId;
    private String modelName;
    private String inputSummary;
    private String outputSummary;
    private Integer tokenInput;
    private Integer tokenOutput;
    private Long durationMs;
}
