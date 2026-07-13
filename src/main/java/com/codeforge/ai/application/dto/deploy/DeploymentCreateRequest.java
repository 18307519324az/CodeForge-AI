package com.codeforge.ai.application.dto.deploy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeploymentCreateRequest {

    @NotNull
    private Long appId;

    @NotNull
    private Long appVersionId;

    @NotBlank
    private String environmentCode;

    @NotBlank
    private String deployTarget;

    private String runtimeConfigJson;
}
