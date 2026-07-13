package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.auth.LoginResponse;
import com.codeforge.ai.application.dto.auth.RegisterResponse;
import com.codeforge.ai.application.dto.auth.UserLoginRequest;
import com.codeforge.ai.application.dto.auth.UserRegisterRequest;
import com.codeforge.ai.application.service.AuthApplicationService;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final PreviewAccessTokenService previewAccessTokenService;

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(
            @Valid @RequestBody UserRegisterRequest request,
            HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, previewAccessTokenService.buildExpiredPreviewCookie().toString());
        return ResultUtils.success(authApplicationService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody UserLoginRequest request,
            HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, previewAccessTokenService.buildExpiredPreviewCookie().toString());
        return ResultUtils.success(authApplicationService.login(request));
    }
}
