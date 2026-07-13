package com.codeforge.ai.application.dto.app;

import com.codeforge.ai.shared.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiAppQueryRequest extends PageRequest {

    private Long workspaceId;
    private String status;
    private String appType;
}
