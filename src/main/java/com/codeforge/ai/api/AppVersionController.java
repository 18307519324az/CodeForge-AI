package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.app.AppVersionDetailResponse;
import com.codeforge.ai.application.dto.app.AppVersionDiffResponse;
import com.codeforge.ai.application.dto.app.AppVersionFileContentResponse;
import com.codeforge.ai.application.dto.app.AppVersionFileResponse;
import com.codeforge.ai.application.dto.app.AppVersionListItemResponse;
import com.codeforge.ai.application.dto.app.AppVersionRepairResponse;
import com.codeforge.ai.application.dto.app.AppVersionRollbackResponse;
import com.codeforge.ai.application.service.AppVersionApplicationService;
import com.codeforge.ai.application.service.GeneratedArtifactRepairApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/v1/apps/{appId}/versions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AppVersionController {

    private final AppVersionApplicationService appVersionApplicationService;
    private final GeneratedArtifactRepairApplicationService generatedArtifactRepairApplicationService;

    @GetMapping
    public ApiResponse<PageResponse<AppVersionListItemResponse>> listVersions(@AuthenticationPrincipal CurrentUser currentUser,
                                                                              @PathVariable Long appId,
                                                                              @ModelAttribute PageRequest request) {
        return ResultUtils.success(appVersionApplicationService.listVersions(currentUser, appId, request));
    }

    @GetMapping("/{versionId}")
    public ApiResponse<AppVersionDetailResponse> getVersion(@AuthenticationPrincipal CurrentUser currentUser,
                                                            @PathVariable Long appId,
                                                            @PathVariable Long versionId) {
        return ResultUtils.success(appVersionApplicationService.getVersion(currentUser, appId, versionId));
    }

    @GetMapping("/{versionId}/files")
    public ApiResponse<List<AppVersionFileResponse>> listVersionFiles(@AuthenticationPrincipal CurrentUser currentUser,
                                                                      @PathVariable Long appId,
                                                                      @PathVariable Long versionId) {
        return ResultUtils.success(appVersionApplicationService.listVersionFiles(currentUser, appId, versionId));
    }

    @GetMapping("/{versionId}/files/content")
    public ApiResponse<AppVersionFileContentResponse> getFileContent(@AuthenticationPrincipal CurrentUser currentUser,
                                                                     @PathVariable Long appId,
                                                                     @PathVariable Long versionId,
                                                                     @RequestParam String filePath) {
        return ResultUtils.success(appVersionApplicationService.getFileContent(currentUser, appId, versionId, filePath));
    }

    @GetMapping("/diff")
    public ApiResponse<AppVersionDiffResponse> diffVersions(@AuthenticationPrincipal CurrentUser currentUser,
                                                            @PathVariable Long appId,
                                                         @RequestParam Long fromVersionId,
                                                         @RequestParam Long toVersionId) {
        return ResultUtils.success(appVersionApplicationService.diffVersions(currentUser, appId, fromVersionId, toVersionId));
    }

    @PostMapping("/{versionId}/rollback")
    public ApiResponse<AppVersionRollbackResponse> rollbackVersion(@AuthenticationPrincipal CurrentUser currentUser,
                                                            @PathVariable Long appId,
                                                            @PathVariable Long versionId) {
        return ResultUtils.success(appVersionApplicationService.rollbackVersion(currentUser, appId, versionId));
    }

    @PostMapping("/{versionId}/repair")
    public ApiResponse<AppVersionRepairResponse> repairVersion(@AuthenticationPrincipal CurrentUser currentUser,
                                                              @PathVariable Long appId,
                                                              @PathVariable Long versionId) {
        return ResultUtils.success(
                generatedArtifactRepairApplicationService.repairArtifactVersion(currentUser, appId, versionId));
    }
}
