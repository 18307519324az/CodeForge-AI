package com.codeforge.ai.application.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @Size(max = 128, message = "displayName 长度不能超过 128")
    private String displayName;

    @Size(max = 1024, message = "avatarUrl 长度不能超过 1024")
    private String avatarUrl;

    @Email(message = "email 格式非法")
    @Size(max = 256, message = "email 长度不能超过 256")
    private String email;

    @Size(max = 64, message = "phone 长度不能超过 64")
    private String phone;
}
