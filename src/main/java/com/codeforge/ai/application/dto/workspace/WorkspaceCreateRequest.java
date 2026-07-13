package com.codeforge.ai.application.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkspaceCreateRequest {

    @NotBlank(message = "name 不能为空")
    @Size(max = 128, message = "name 长度不能超过 128")
    private String name;

    @Size(max = 512, message = "description 长度不能超过 512")
    private String description;
}
