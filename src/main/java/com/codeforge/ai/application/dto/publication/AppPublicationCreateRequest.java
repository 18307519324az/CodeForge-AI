package com.codeforge.ai.application.dto.publication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppPublicationCreateRequest(
        @NotNull(message = "versionId 不能为空")
        Long versionId,
        @NotBlank(message = "公开标题不能为空")
        @Size(max = 128, message = "公开标题不能超过 128 个字符")
        String publicTitle,
        @Size(max = 1024, message = "公开简介不能超过 1024 个字符")
        String publicDescription,
        @NotNull(message = "allowPreview 不能为空")
        Boolean allowPreview,
        @NotNull(message = "allowDownload 不能为空")
        Boolean allowDownload
) {
}
