package com.codeforge.ai.application.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

@Data
public class GenerationTaskCreateRequest {

    @NotNull(message = "workspaceId 不能为空")
    private Long workspaceId;

    @NotNull(message = "appId 不能为空")
    private Long appId;

    @NotBlank(message = "taskType 不能为空")
    @Size(max = 64, message = "taskType 长度不能超过 64")
    private String taskType;

    private Long promptTemplateId;

    @Positive(message = "promptTemplateVersionId 必须大于 0")
    private Long promptTemplateVersionId;

    @Positive(message = "promptTemplateVersionNo 必须大于 0")
    private Integer promptTemplateVersionNo;

    private Map<String, String> templateVariables;

    @NotBlank(message = "requirement 不能为空")
    private String requirement;

    @Size(max = 128, message = "idempotencyKey 长度不能超过 128")
    private String idempotencyKey;
}
