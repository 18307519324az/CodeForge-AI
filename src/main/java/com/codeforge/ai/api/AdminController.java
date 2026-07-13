package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.app.AiAppListItemResponse;
import com.codeforge.ai.application.dto.admin.AdminUserListItemResponse;
import com.codeforge.ai.application.dto.admin.AuditLogResponse;
import com.codeforge.ai.application.dto.admin.MetricRefreshResponse;
import com.codeforge.ai.application.dto.admin.MetricSummaryResponse;
import com.codeforge.ai.application.dto.admin.ModelCallLogResponse;
import com.codeforge.ai.application.dto.admin.AiRoutingConfigResponse;
import com.codeforge.ai.application.dto.admin.AiRoutingConfigUpdateRequest;
import com.codeforge.ai.application.dto.admin.ModelProviderCreateRequest;
import com.codeforge.ai.application.dto.admin.ModelProviderResponse;
import com.codeforge.ai.application.dto.admin.ModelProviderStatusUpdateRequest;
import com.codeforge.ai.application.dto.admin.ModelProviderUpdateRequest;
import com.codeforge.ai.application.dto.admin.ProviderCredentialResponse;
import com.codeforge.ai.application.dto.admin.ProviderCredentialUpsertRequest;
import com.codeforge.ai.application.dto.admin.PromptRuntimeBindingGateResponse;
import com.codeforge.ai.application.dto.admin.PromptTemplateTestRunRequest;
import com.codeforge.ai.application.dto.admin.PromptTemplateTestRunResponse;
import com.codeforge.ai.application.dto.admin.ProviderHealthCheckResponse;
import com.codeforge.ai.application.service.AdminPromptTemplateApplicationService;
import com.codeforge.ai.application.dto.admin.QuotaAdjustRequest;
import com.codeforge.ai.application.service.AdminAiRoutingApplicationService;
import com.codeforge.ai.application.service.AdminModelProviderApplicationService;
import com.codeforge.ai.application.dto.quota.QuotaUsageLogResponse;
import com.codeforge.ai.application.dto.quota.UserQuotaResponse;
import com.codeforge.ai.application.service.AdminMetricsApplicationService;
import com.codeforge.ai.application.service.AdminQueryApplicationService;
import com.codeforge.ai.application.service.QuotaApplicationService;
import com.codeforge.ai.application.service.release.AdminPromptRuntimeGateApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final QuotaApplicationService quotaApplicationService;
    private final AdminQueryApplicationService adminQueryApplicationService;
    private final AdminMetricsApplicationService adminMetricsApplicationService;
    private final AdminModelProviderApplicationService adminModelProviderApplicationService;
    private final AdminAiRoutingApplicationService adminAiRoutingApplicationService;
    private final AdminPromptTemplateApplicationService adminPromptTemplateApplicationService;
    private final AdminPromptRuntimeGateApplicationService adminPromptRuntimeGateApplicationService;

    @GetMapping("/apps")
    public ApiResponse<PageResponse<AiAppListItemResponse>> listAdminApps(@AuthenticationPrincipal CurrentUser currentUser,
                                                                          @ModelAttribute PageRequest pageRequest) {
        return ResultUtils.success(adminQueryApplicationService.listAdminApps(currentUser, pageRequest));
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserListItemResponse>> listUsers(@AuthenticationPrincipal CurrentUser currentUser,
                                                                          @ModelAttribute PageRequest pageRequest) {
        return ResultUtils.success(adminQueryApplicationService.listUsers(currentUser, pageRequest));
    }

    @PostMapping("/quotas/adjust")
    public ApiResponse<UserQuotaResponse> adjustQuota(@AuthenticationPrincipal CurrentUser currentUser,
                                                      @Valid @RequestBody QuotaAdjustRequest request) {
        return ResultUtils.success(quotaApplicationService.adjustQuota(currentUser, request));
    }

    @GetMapping("/quotas/usage-logs")
    public ApiResponse<List<QuotaUsageLogResponse>> listQuotaUsageLogs(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResultUtils.success(quotaApplicationService.listQuotaUsageLogs(currentUser));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AuditLogResponse>> listAuditLogs(@AuthenticationPrincipal CurrentUser currentUser,
                                                                     @ModelAttribute PageRequest pageRequest) {
        return ResultUtils.success(adminQueryApplicationService.listAuditLogs(currentUser, pageRequest));
    }

    @GetMapping("/metrics/summary")
    public ApiResponse<MetricSummaryResponse> getMetricsSummary(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResultUtils.success(adminQueryApplicationService.getMetricsSummary(currentUser));
    }

    @PostMapping("/metrics/refresh")
    public ApiResponse<MetricRefreshResponse> refreshMetrics(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResultUtils.success(adminMetricsApplicationService.refreshMetrics(currentUser));
    }

    @GetMapping("/model-providers")
    public ApiResponse<List<ModelProviderResponse>> listModelProviders(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResultUtils.success(adminQueryApplicationService.listModelProviders(currentUser));
    }

    @PostMapping("/model-providers")
    public ApiResponse<ModelProviderResponse> createModelProvider(@AuthenticationPrincipal CurrentUser currentUser,
                                                                  @Valid @RequestBody ModelProviderCreateRequest request) {
        return ResultUtils.success(adminModelProviderApplicationService.createModelProvider(currentUser, request));
    }

    @PutMapping("/model-providers/{providerId}")
    public ApiResponse<ModelProviderResponse> updateModelProvider(@AuthenticationPrincipal CurrentUser currentUser,
                                                                  @PathVariable Long providerId,
                                                                  @Valid @RequestBody ModelProviderUpdateRequest request) {
        return ResultUtils.success(adminModelProviderApplicationService.updateModelProvider(currentUser, providerId, request));
    }

    @PutMapping("/model-providers/{providerId}/credential")
    public ApiResponse<ProviderCredentialResponse> upsertProviderCredential(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long providerId,
            @Valid @RequestBody ProviderCredentialUpsertRequest request) {
        return ResultUtils.success(adminModelProviderApplicationService.upsertProviderCredential(
                currentUser, providerId, request));
    }

    @DeleteMapping("/model-providers/{providerId}/credential")
    public ApiResponse<ProviderCredentialResponse> deleteProviderCredential(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long providerId) {
        return ResultUtils.success(adminModelProviderApplicationService.deleteProviderCredential(currentUser, providerId));
    }

    @GetMapping("/ai-routing")
    public ApiResponse<AiRoutingConfigResponse> getAiRouting(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResultUtils.success(adminAiRoutingApplicationService.getAiRouting(currentUser));
    }

    @PutMapping("/ai-routing")
    public ApiResponse<AiRoutingConfigResponse> updateAiRouting(@AuthenticationPrincipal CurrentUser currentUser,
                                                                @Valid @RequestBody AiRoutingConfigUpdateRequest request) {
        return ResultUtils.success(adminAiRoutingApplicationService.updateAiRouting(currentUser, request));
    }

    @PutMapping("/model-providers/{providerId}/status")
    public ApiResponse<ModelProviderResponse> updateModelProviderStatus(@AuthenticationPrincipal CurrentUser currentUser,
                                                                          @PathVariable Long providerId,
                                                                          @Valid @RequestBody ModelProviderStatusUpdateRequest request) {
        return ResultUtils.success(adminModelProviderApplicationService.updateModelProviderStatus(
                currentUser, providerId, request));
    }

    @PostMapping("/model-providers/{providerId}/health-check")
    public ApiResponse<ProviderHealthCheckResponse> healthCheckModelProvider(@AuthenticationPrincipal CurrentUser currentUser,
                                                                             @PathVariable Long providerId) {
        return ResultUtils.success(adminModelProviderApplicationService.healthCheckModelProvider(currentUser, providerId));
    }

    @PostMapping("/prompt-templates/{templateId}/test-run")
    public ApiResponse<PromptTemplateTestRunResponse> testRunPromptTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId,
            @Valid @RequestBody PromptTemplateTestRunRequest request) {
        return ResultUtils.success(adminPromptTemplateApplicationService.testRunTemplate(currentUser, templateId, request));
    }

    @GetMapping("/model-call-logs")
    public ApiResponse<PageResponse<ModelCallLogResponse>> listModelCallLogs(@AuthenticationPrincipal CurrentUser currentUser,
                                                                              @ModelAttribute PageRequest pageRequest) {
        return ResultUtils.success(adminQueryApplicationService.listModelCallLogs(currentUser, pageRequest));
    }

    @GetMapping("/release-gates/prompt-runtime-binding")
    public ApiResponse<PromptRuntimeBindingGateResponse> verifyPromptRuntimeBinding(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long taskId,
            @RequestParam(required = false) Long modelCallId) {
        return ResultUtils.success(adminPromptRuntimeGateApplicationService.verifyPromptRuntimeBinding(
                currentUser, taskId, modelCallId));
    }
}
