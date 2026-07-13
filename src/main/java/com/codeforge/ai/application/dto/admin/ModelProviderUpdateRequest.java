package com.codeforge.ai.application.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModelProviderUpdateRequest {

    @NotBlank
    @Size(max = 128)
    private String providerName;

    @Size(max = 1024)
    private String baseUrl;

    @NotBlank
    @Size(max = 32)
    private String authMode;

    @Size(max = 128)
    private String defaultModel;

    private Integer priority;

    @Size(max = 32)
    private String status;

    @Size(max = 32)
    private String credentialSource;
}
