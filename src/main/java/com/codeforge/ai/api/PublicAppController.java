package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.app.AppVersionPreviewTokenResponse;
import com.codeforge.ai.application.dto.publication.PublicAppDetailResponse;
import com.codeforge.ai.application.dto.publication.PublicAppListItemResponse;
import com.codeforge.ai.application.dto.publication.PublicAppQueryRequest;
import com.codeforge.ai.application.dto.publication.PublicAppViewResponse;
import com.codeforge.ai.application.dto.publication.PublicDownloadTokenResponse;
import com.codeforge.ai.application.service.PublicAppApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public/apps")
@RequiredArgsConstructor
public class PublicAppController {

    private final PublicAppApplicationService publicAppApplicationService;
    private final PreviewAccessTokenService previewAccessTokenService;

    @GetMapping
    public ApiResponse<PageResponse<PublicAppListItemResponse>> listPublishedApps(
            @RequestParam(required = false) Long pageNo,
            @RequestParam(required = false) Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String appType,
            @RequestParam(required = false) String sort) {
        return ResultUtils.success(publicAppApplicationService.listPublishedApps(
                new PublicAppQueryRequest(pageNo, pageSize, keyword, appType, sort)));
    }

    @GetMapping("/{slug}")
    public ApiResponse<PublicAppDetailResponse> getPublishedAppDetail(@PathVariable String slug) {
        return ResultUtils.success(publicAppApplicationService.getPublishedAppDetail(slug));
    }

    @PostMapping("/{slug}/preview-token")
    public ApiResponse<AppVersionPreviewTokenResponse> issuePublicPreviewToken(
            @PathVariable String slug,
            HttpServletResponse response) {
        AppVersionPreviewTokenResponse previewResponse = publicAppApplicationService.issuePublicPreviewToken(slug);
        String previewToken = extractPreviewToken(previewResponse.previewUrl());
        response.addHeader(HttpHeaders.SET_COOKIE, previewAccessTokenService.buildPreviewCookie(previewToken).toString());
        return ResultUtils.success(previewResponse);
    }

    @PostMapping("/{slug}/download-token")
    public ApiResponse<PublicDownloadTokenResponse> issuePublicDownloadToken(@PathVariable String slug) {
        return ResultUtils.success(publicAppApplicationService.issuePublicDownloadToken(slug));
    }

    @PostMapping("/{slug}/view")
    public ApiResponse<PublicAppViewResponse> recordPublicAppView(
            @PathVariable String slug,
            @AuthenticationPrincipal CurrentUser currentUser,
            HttpServletRequest request,
            HttpServletResponse response) {
        Long userId = currentUser == null ? null : currentUser.userId();
        return ResultUtils.success(publicAppApplicationService.recordPublicAppView(slug, userId, request, response));
    }

    private String extractPreviewToken(String previewUrl) {
        int index = previewUrl.indexOf("previewToken=");
        if (index < 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "预览 token 生成失败");
        }
        return previewUrl.substring(index + "previewToken=".length());
    }
}
