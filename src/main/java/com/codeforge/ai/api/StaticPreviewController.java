package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.app.AppVersionPreviewTokenResponse;
import com.codeforge.ai.application.service.StaticPreviewApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class StaticPreviewController {

    private final StaticPreviewApplicationService staticPreviewApplicationService;
    private final PreviewAccessTokenService previewAccessTokenService;

    @PostMapping("/apps/{appId}/versions/{versionId}/preview-token")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<AppVersionPreviewTokenResponse> createPreviewToken(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long appId,
            @PathVariable Long versionId,
            HttpServletResponse response) {
        AppVersionPreviewTokenResponse previewResponse =
                staticPreviewApplicationService.issuePreviewToken(currentUser, appId, versionId);
        String previewToken = extractPreviewToken(previewResponse.previewUrl());
        response.addHeader(HttpHeaders.SET_COOKIE, previewAccessTokenService.buildPreviewCookie(previewToken).toString());
        return ResultUtils.success(previewResponse);
    }

    @GetMapping("/static-preview/{versionId}/**")
    public ResponseEntity<byte[]> getPreviewFile(
            @PathVariable Long versionId,
            @RequestParam(required = false) String previewToken,
            @CookieValue(name = PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, required = false) String previewCookieToken,
            HttpServletRequest request) {
        try {
            String filePath = extractFilePath(request, versionId);
            var previewFile = staticPreviewApplicationService.loadPreviewFile(
                    versionId, filePath, previewToken, previewCookieToken);
            MediaType contentType = withUtf8CharsetIfText(previewFile.mediaType());
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(previewFile.content());
        } catch (BusinessException exception) {
            return buildErrorResponse(exception.getErrorCode());
        }
    }

    private ResponseEntity<byte[]> buildErrorResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .header(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-8")
                .body(errorCode.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String extractFilePath(HttpServletRequest request, Long versionId) {
        String uri = request.getRequestURI();
        String prefix = request.getContextPath() + "/v1/static-preview/" + versionId + "/";
        if (!uri.startsWith(prefix)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return uri.substring(prefix.length());
    }

    private String extractPreviewToken(String previewUrl) {
        int index = previewUrl.indexOf("previewToken=");
        if (index < 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "预览 token 生成失败");
        }
        return previewUrl.substring(index + "previewToken=".length());
    }

    private MediaType withUtf8CharsetIfText(MediaType mediaType) {
        if (mediaType == null) {
            return MediaType.TEXT_PLAIN;
        }
        if (MediaType.TEXT_HTML.isCompatibleWith(mediaType)
                || MediaType.TEXT_PLAIN.isCompatibleWith(mediaType)
                || MediaType.valueOf("text/css").isCompatibleWith(mediaType)
                || MediaType.valueOf("application/javascript").isCompatibleWith(mediaType)) {
            Charset charset = mediaType.getCharset();
            if (charset == null || !StandardCharsets.UTF_8.equals(charset)) {
                return new MediaType(mediaType, StandardCharsets.UTF_8);
            }
        }
        return mediaType;
    }
}
