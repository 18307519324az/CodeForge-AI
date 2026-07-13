package com.codeforge.ai.application.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptTemplateCreateRequest {

    @NotNull(message = "workspaceId 不能为空")
    private Long workspaceId;

    @NotBlank(message = "templateName 不能为空")
    @Size(max = 128, message = "templateName 长度不能超过 128")
    private String templateName;

    @NotBlank(message = "templateScene 不能为空")
    @Size(max = 64, message = "templateScene 长度不能超过 64")
    private String templateScene;

    @NotBlank(message = "systemPrompt 不能为空")
    private String systemPrompt;

    @NotBlank(message = "userPrompt 不能为空")
    private String userPrompt;

    @Size(max = 512, message = "remark 长度不能超过 512")
    private String remark;
}
