package com.codeforge.ai.application.dto.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExportPackageCreateRequest {

    @NotNull
    private Long appId;

    @NotNull
    private Long appVersionId;

    @NotBlank
    private String packageType;
}
