package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.app.AiAppCreateRequest;
import com.codeforge.ai.application.dto.app.AiAppDetailResponse;
import com.codeforge.ai.application.dto.app.AiAppListItemResponse;
import com.codeforge.ai.application.dto.app.AiAppQueryRequest;
import com.codeforge.ai.application.dto.app.AiAppUpdateRequest;
import com.codeforge.ai.application.service.AiAppApplicationService;
import com.codeforge.ai.application.service.GenerationTaskApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.application.dto.task.GenerationRecordResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/apps")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AiAppController {

    private final AiAppApplicationService aiAppApplicationService;
    private final GenerationTaskApplicationService generationTaskApplicationService;

    @PostMapping
    public ApiResponse<AiAppDetailResponse> createApp(@AuthenticationPrincipal CurrentUser currentUser,
                                                      @Valid @RequestBody AiAppCreateRequest request) {
        return ResultUtils.success(aiAppApplicationService.createApp(currentUser, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<AiAppListItemResponse>> listApps(@AuthenticationPrincipal CurrentUser currentUser,
                                                                     @ModelAttribute AiAppQueryRequest request) {
        return ResultUtils.success(aiAppApplicationService.listApps(currentUser, request));
    }

    @GetMapping("/{appId}")
    public ApiResponse<AiAppDetailResponse> getApp(@AuthenticationPrincipal CurrentUser currentUser,
                                                   @PathVariable Long appId) {
        return ResultUtils.success(aiAppApplicationService.getApp(currentUser, appId));
    }

    @GetMapping("/{appId}/generation-records")
    public ApiResponse<List<GenerationRecordResponse>> listGenerationRecords(@AuthenticationPrincipal CurrentUser currentUser,
                                                                             @PathVariable Long appId) {
        return ResultUtils.success(generationTaskApplicationService.listGenerationRecords(currentUser, appId));
    }

    @PutMapping("/{appId}")
    public ApiResponse<AiAppDetailResponse> updateApp(@AuthenticationPrincipal CurrentUser currentUser,
                                                      @PathVariable Long appId,
                                                      @Valid @RequestBody AiAppUpdateRequest request) {
        return ResultUtils.success(aiAppApplicationService.updateApp(currentUser, appId, request));
    }

    @PostMapping("/{appId}/archive")
    public ApiResponse<AiAppDetailResponse> archiveApp(@AuthenticationPrincipal CurrentUser currentUser,
                                                       @PathVariable Long appId) {
        return ResultUtils.success(aiAppApplicationService.archiveApp(currentUser, appId));
    }

    @DeleteMapping("/{appId}")
    public ApiResponse<Void> deleteApp(@AuthenticationPrincipal CurrentUser currentUser,
                                       @PathVariable Long appId) {
        aiAppApplicationService.deleteApp(currentUser, appId);
        return ResultUtils.success();
    }
}
