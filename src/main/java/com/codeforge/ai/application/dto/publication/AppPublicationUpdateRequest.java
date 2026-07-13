package com.codeforge.ai.application.dto.publication;

import jakarta.validation.constraints.Size;

public record AppPublicationUpdateRequest(
        Long versionId,
        @Size(max = 128, message = "公开标题不能超过 128 个字符")
        String publicTitle,
        @Size(max = 1024, message = "公开简介不能超过 1024 个字符")
        String publicDescription,
        Boolean allowPreview,
        Boolean allowDownload
) {
}
