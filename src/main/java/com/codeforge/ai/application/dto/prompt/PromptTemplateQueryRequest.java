package com.codeforge.ai.application.dto.prompt;

import com.codeforge.ai.shared.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PromptTemplateQueryRequest extends PageRequest {

    private Long workspaceId;
    private String templateScene;
    private String status;
}
