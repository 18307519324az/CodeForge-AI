package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.deploy.DeploymentCreateRequest;
import com.codeforge.ai.application.dto.deploy.DeploymentCreateResponse;
import com.codeforge.ai.application.dto.deploy.DeploymentDetailResponse;
import com.codeforge.ai.application.dto.deploy.DeploymentLogResponse;
import com.codeforge.ai.application.service.DeploymentApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/deployments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DeploymentController {

    private final DeploymentApplicationService deploymentApplicationService;

    @PostMapping
    public ApiResponse<DeploymentCreateResponse> createDeployment(@AuthenticationPrincipal CurrentUser currentUser,
                                                                  @Valid @RequestBody DeploymentCreateRequest request) {
        return ResultUtils.success(deploymentApplicationService.createDeployment(currentUser, request));
    }

    @GetMapping("/{deploymentJobId}")
    public ApiResponse<DeploymentDetailResponse> getDeployment(@AuthenticationPrincipal CurrentUser currentUser,
                                                               @PathVariable Long deploymentJobId) {
        return ResultUtils.success(deploymentApplicationService.getDeployment(currentUser, deploymentJobId));
    }

    @GetMapping("/{deploymentJobId}/logs")
    public ApiResponse<List<DeploymentLogResponse>> getDeploymentLogs(@AuthenticationPrincipal CurrentUser currentUser,
                                                                      @PathVariable Long deploymentJobId) {
        return ResultUtils.success(deploymentApplicationService.getDeploymentLogs(currentUser, deploymentJobId));
    }
}
