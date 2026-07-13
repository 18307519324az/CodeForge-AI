package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.user.CurrentUserResponse;
import com.codeforge.ai.application.dto.user.UserUpdateRequest;
import com.codeforge.ai.application.service.UserApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserApplicationService userApplicationService;

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResultUtils.success(userApplicationService.getCurrentUser(currentUser));
    }

    @PutMapping("/me")
    public ApiResponse<CurrentUserResponse> updateCurrentUser(@AuthenticationPrincipal CurrentUser currentUser,
                                                              @Valid @RequestBody UserUpdateRequest request) {
        return ResultUtils.success(userApplicationService.updateCurrentUser(currentUser, request));
    }
}
