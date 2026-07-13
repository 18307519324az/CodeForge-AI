package com.codeforge.ai.application.dto.app;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAppUpdateRequest {

    @Size(max = 128, message = "name 长度不能超过 128")
    private String name;

    @Size(max = 512, message = "description 长度不能超过 512")
    private String description;

    @Size(max = 1024, message = "coverUrl 长度不能超过 1024")
    private String coverUrl;

    @Size(max = 32, message = "visibility 长度不能超过 32")
    private String visibility;
}
