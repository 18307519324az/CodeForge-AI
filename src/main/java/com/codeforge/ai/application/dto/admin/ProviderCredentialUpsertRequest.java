package com.codeforge.ai.application.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProviderCredentialUpsertRequest {

    @NotBlank
    @Size(min = 8, max = 512)
    private String apiKey;
}
