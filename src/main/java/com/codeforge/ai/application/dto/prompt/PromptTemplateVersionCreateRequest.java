package com.codeforge.ai.application.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptTemplateVersionCreateRequest {

    @NotBlank(message = "systemPrompt 不能为空")
    private String systemPrompt;

    @NotBlank(message = "userPrompt 不能为空")
    private String userPrompt;

    private String variablesJson;

    private String modelStrategyJson;
}
