package com.codeforge.ai.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginRequest {

    @NotBlank(message = "account 不能为空")
    private String account;

    @NotBlank(message = "password 不能为空")
    private String password;
}
