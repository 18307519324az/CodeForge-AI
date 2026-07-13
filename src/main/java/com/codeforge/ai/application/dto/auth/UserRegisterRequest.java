package com.codeforge.ai.application.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterRequest {

    @NotBlank(message = "account 不能为空")
    @Size(max = 128, message = "account 长度不能超过 128")
    private String account;

    @NotBlank(message = "password 不能为空")
    @Size(min = 8, max = 64, message = "password 长度必须在 8 到 64 之间")
    private String password;

    @NotBlank(message = "confirmPassword 不能为空")
    private String confirmPassword;

    @NotBlank(message = "displayName 不能为空")
    @Size(max = 128, message = "displayName 长度不能超过 128")
    private String displayName;

    @Email(message = "email 格式非法")
    @Size(max = 256, message = "email 长度不能超过 256")
    private String email;
}
