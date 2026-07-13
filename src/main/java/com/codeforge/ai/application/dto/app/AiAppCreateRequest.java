package com.codeforge.ai.application.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAppCreateRequest {

    @NotNull(message = "workspaceId 不能为空")
    private Long workspaceId;

    @NotBlank(message = "name 不能为空")
    @Size(max = 128, message = "name 长度不能超过 128")
    private String name;

    @Size(max = 512, message = "description 长度不能超过 512")
    private String description;

    @NotBlank(message = "appType 不能为空")
    @Size(max = 64, message = "appType 长度不能超过 64")
    private String appType;
}
