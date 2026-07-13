package com.codeforge.ai.application.dto.admin;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
public class PromptTemplateTestRunRequest {

    private Integer versionNo;

    @NotNull(message = "mockVariables 不能为空")
    private Map<String, String> mockVariables;
}
