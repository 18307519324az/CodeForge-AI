package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.prompt.PromptTemplateCreateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateListItemResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateQueryRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUpdateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserDetailResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateUserListItemResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplatePublishedVersionResponse;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionCreateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionUpdateRequest;
import com.codeforge.ai.application.dto.prompt.PromptTemplateVersionResponse;
import com.codeforge.ai.application.dto.prompt.PublishedPromptTemplateQueryRequest;
import com.codeforge.ai.application.service.PromptTemplateApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
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
@RequestMapping("/v1/prompt-templates")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PromptTemplateController {

    private final PromptTemplateApplicationService promptTemplateApplicationService;

    @PostMapping
    public ApiResponse<PromptTemplateDetailResponse> createTemplate(@AuthenticationPrincipal CurrentUser currentUser,
                                                                    @Valid @RequestBody PromptTemplateCreateRequest request) {
        return ResultUtils.success(promptTemplateApplicationService.createTemplate(currentUser, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<PromptTemplateListItemResponse>> listTemplates(
            @AuthenticationPrincipal CurrentUser currentUser,
            @ModelAttribute PromptTemplateQueryRequest request) {
        return ResultUtils.success(promptTemplateApplicationService.listTemplates(currentUser, request));
    }

    @GetMapping("/published")
    public ApiResponse<PageResponse<PromptTemplateUserListItemResponse>> listPublishedTemplates(
            @AuthenticationPrincipal CurrentUser currentUser,
            @ModelAttribute PublishedPromptTemplateQueryRequest request) {
        return ResultUtils.success(promptTemplateApplicationService.listPublishedTemplates(currentUser, request));
    }

    @GetMapping("/published/{templateId}")
    public ApiResponse<PromptTemplateUserDetailResponse> getPublishedTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId) {
        return ResultUtils.success(promptTemplateApplicationService.getPublishedTemplate(currentUser, templateId));
    }

    @GetMapping("/published/{templateId}/versions")
    public ApiResponse<List<PromptTemplatePublishedVersionResponse>> listPublishedTemplateVersions(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId) {
        return ResultUtils.success(promptTemplateApplicationService.listPublishedTemplateVersions(currentUser, templateId));
    }

    @GetMapping("/{templateId}")
    public ApiResponse<PromptTemplateDetailResponse> getTemplate(@AuthenticationPrincipal CurrentUser currentUser,
                                                                 @PathVariable Long templateId) {
        return ResultUtils.success(promptTemplateApplicationService.getTemplate(currentUser, templateId));
    }

    @GetMapping("/{templateId}/versions")
    public ApiResponse<List<PromptTemplateVersionResponse>> listTemplateVersions(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId) {
        return ResultUtils.success(promptTemplateApplicationService.listTemplateVersions(currentUser, templateId));
    }

    @PutMapping("/{templateId}")
    public ApiResponse<PromptTemplateDetailResponse> updateTemplate(@AuthenticationPrincipal CurrentUser currentUser,
                                                                    @PathVariable Long templateId,
                                                                    @Valid @RequestBody PromptTemplateUpdateRequest request) {
        return ResultUtils.success(promptTemplateApplicationService.updateTemplate(currentUser, templateId, request));
    }

    @PostMapping("/{templateId}/versions")
    public ApiResponse<PromptTemplateVersionResponse> createTemplateVersion(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId,
            @Valid @RequestBody PromptTemplateVersionCreateRequest request) {
        return ResultUtils.success(promptTemplateApplicationService.createTemplateVersion(currentUser, templateId, request));
    }

    @PostMapping("/{templateId}/versions/{versionNo}/publish")
    public ApiResponse<PromptTemplateDetailResponse> publishTemplateVersion(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId,
            @PathVariable Integer versionNo) {
        return ResultUtils.success(promptTemplateApplicationService.publishTemplateVersion(currentUser, templateId, versionNo));
    }

    @GetMapping("/{templateId}/versions/{versionNo}")
    public ApiResponse<PromptTemplateVersionResponse> getTemplateVersion(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId,
            @PathVariable Integer versionNo) {
        return ResultUtils.success(promptTemplateApplicationService.getTemplateVersion(currentUser, templateId, versionNo));
    }

    @PutMapping("/{templateId}/versions/{versionNo}")
    public ApiResponse<PromptTemplateVersionResponse> updateTemplateVersion(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId,
            @PathVariable Integer versionNo,
            @Valid @RequestBody PromptTemplateVersionUpdateRequest request) {
        return ResultUtils.success(
                promptTemplateApplicationService.updateTemplateVersion(currentUser, templateId, versionNo, request));
    }

    @DeleteMapping("/{templateId}/versions/{versionNo}")
    public ApiResponse<Void> deleteTemplateVersion(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId,
            @PathVariable Integer versionNo) {
        promptTemplateApplicationService.deleteTemplateVersion(currentUser, templateId, versionNo);
        return ResultUtils.success(null);
    }

    @PostMapping("/{templateId}/archive")
    public ApiResponse<PromptTemplateDetailResponse> archiveTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId) {
        return ResultUtils.success(promptTemplateApplicationService.archiveTemplate(currentUser, templateId));
    }

    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> deleteTemplate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long templateId) {
        promptTemplateApplicationService.deleteTemplate(currentUser, templateId);
        return ResultUtils.success(null);
    }
}
