package com.codeforge.ai.application.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiRoutingConfigUpdateRequest {

    @NotBlank
    @Size(max = 16)
    private String mode;

    @Size(max = 64)
    private String providerCode;
}
