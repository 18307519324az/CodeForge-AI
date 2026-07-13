package com.codeforge.ai.application.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModelProviderStatusUpdateRequest {

    @NotBlank(message = "status 不能为空")
    private String status;
}
