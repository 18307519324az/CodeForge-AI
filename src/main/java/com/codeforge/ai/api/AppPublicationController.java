package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.publication.AppPublicationCreateRequest;
import com.codeforge.ai.application.dto.publication.AppPublicationResponse;
import com.codeforge.ai.application.dto.publication.AppPublicationUpdateRequest;
import com.codeforge.ai.application.service.AppPublicationApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/apps/{appId}/publications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AppPublicationController {

    private final AppPublicationApplicationService appPublicationApplicationService;

    @PostMapping
    public ApiResponse<AppPublicationResponse> publishApp(@AuthenticationPrincipal CurrentUser currentUser,
                                                        @PathVariable Long appId,
                                                        @Valid @RequestBody AppPublicationCreateRequest request) {
        return ResultUtils.success(appPublicationApplicationService.publishApp(currentUser, appId, request));
    }

    @GetMapping("/current")
    public ApiResponse<AppPublicationResponse> getCurrentPublication(@AuthenticationPrincipal CurrentUser currentUser,
                                                                     @PathVariable Long appId) {
        return ResultUtils.success(appPublicationApplicationService.getPublicationForOwner(currentUser, appId));
    }

    @PutMapping("/{publicationId}")
    public ApiResponse<AppPublicationResponse> updatePublication(@AuthenticationPrincipal CurrentUser currentUser,
                                                                 @PathVariable Long appId,
                                                                 @PathVariable Long publicationId,
                                                                 @Valid @RequestBody AppPublicationUpdateRequest request) {
        return ResultUtils.success(
                appPublicationApplicationService.updatePublication(currentUser, appId, publicationId, request));
    }

    @PostMapping("/{publicationId}/unpublish")
    public ApiResponse<AppPublicationResponse> unpublishApp(@AuthenticationPrincipal CurrentUser currentUser,
                                                            @PathVariable Long appId,
                                                            @PathVariable Long publicationId) {
        return ResultUtils.success(
                appPublicationApplicationService.unpublishApp(currentUser, appId, publicationId));
    }
}
