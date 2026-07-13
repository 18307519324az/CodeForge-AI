package com.codeforge.ai.application.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModelProviderCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String providerCode;

    @NotBlank
    @Size(max = 128)
    private String providerName;

    @Size(max = 1024)
    private String baseUrl;

    @NotBlank
    @Size(max = 32)
    private String authMode;
}
