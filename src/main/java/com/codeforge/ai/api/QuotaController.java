package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.quota.UserQuotaResponse;
import com.codeforge.ai.application.service.QuotaApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/quotas")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class QuotaController {

    private final QuotaApplicationService quotaApplicationService;

    @GetMapping("/me")
    public ApiResponse<List<UserQuotaResponse>> getMyQuota(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResultUtils.success(quotaApplicationService.getMyQuotas(currentUser));
    }
}
