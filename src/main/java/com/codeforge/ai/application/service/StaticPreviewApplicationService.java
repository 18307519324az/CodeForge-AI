package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AppVersionPreviewTokenResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaticPreviewApplicationService {

    private static final MediaType MEDIA_TYPE_TEXT_CSS = MediaType.valueOf("text/css");

    private final AiAppEntityMapper aiAppEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final VueProjectBuildService vueProjectBuildService;
    private final PreviewAccessTokenService previewAccessTokenService;
    private final AppPublicationEntityMapper appPublicationEntityMapper;

    public AppVersionPreviewTokenResponse issuePreviewToken(CurrentUser currentUser, Long appId, Long versionId) {
        requireReadableVersion(currentUser, appId, versionId);
        String previewToken = previewAccessTokenService.createPreviewToken(currentUser, versionId);
        return new AppVersionPreviewTokenResponse(
                "/api/v1/static-preview/" + versionId + "/index.html?previewToken=" + previewToken,
                previewAccessTokenService.getPreviewTokenExpireSeconds()
        );
    }

    public StaticPreviewFileResponse loadPreviewFile(
            Long versionId, String filePath, String previewToken, String previewCookieToken) {
        if (previewToken == null || previewToken.isBlank() || previewCookieToken == null || previewCookieToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!previewToken.equals(previewCookieToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ErrorCode tokenError = previewAccessTokenService.resolvePreviewTokenError(
                previewToken, versionId, appPublicationEntityMapper);
        if (tokenError != null) {
            throw new BusinessException(tokenError);
        }

        String normalizedPath = normalizeRequestedFilePath(filePath);
        Optional<byte[]> content = vueProjectBuildService.serveBuiltFile(versionId, normalizedPath);
        if (content.isEmpty()) {
            content = loadGeneratedFileContent(versionId, normalizedPath);
        }
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(normalizedPath)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        byte[] body = maybeRewritePreviewContent(content.get(), normalizedPath, versionId, previewToken, mediaType);
        return new StaticPreviewFileResponse(body, mediaType);
    }

    private AppVersionEntity requireReadableVersion(CurrentUser currentUser, Long appId, Long versionId) {
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireReadAccess(currentUser, appEntity.getWorkspaceId());
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appId, versionId);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应用版本不存在");
        }
        return versionEntity;
    }

    private String normalizeRequestedFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "index.html";
        }
        String normalized = filePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? "index.html" : normalized;
    }

    private Optional<byte[]> loadGeneratedFileContent(Long versionId, String normalizedPath) {
        GeneratedFileEntity fileEntity = generatedFileEntityMapper.findByAppVersionIdAndFilePath(versionId, normalizedPath);
        if (fileEntity == null) {
            return Optional.empty();
        }
        if (fileEntity.getFileContent() != null) {
            return Optional.of(fileEntity.getFileContent().getBytes(StandardCharsets.UTF_8));
        }
        String storagePath = fileEntity.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path resolvedPath = Path.of(storagePath).normalize();
            if (Files.exists(resolvedPath) && Files.isRegularFile(resolvedPath)) {
                return Optional.of(Files.readAllBytes(resolvedPath));
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private byte[] maybeRewritePreviewContent(
            byte[] content, String filePath, Long versionId, String previewToken, MediaType mediaType) {
        String lowercasePath = filePath.toLowerCase();
        if (MediaType.TEXT_HTML.isCompatibleWith(mediaType)
                || MEDIA_TYPE_TEXT_CSS.isCompatibleWith(mediaType)
                || lowercasePath.endsWith(".js")) {
            String text = new String(content, StandardCharsets.UTF_8);
            String rewritten = rewritePreviewReferences(text, versionId, previewToken);
            return rewritten.getBytes(StandardCharsets.UTF_8);
        }
        return content;
    }

    private String rewritePreviewReferences(String content, Long versionId, String previewToken) {
        String rewritten = content;
        rewritten = rewritten.replace("\"/assets/", "\"/api/v1/static-preview/" + versionId + "/assets/");
        rewritten = rewritten.replace("'/assets/", "'/api/v1/static-preview/" + versionId + "/assets/");
        rewritten = rewritten.replace("url(/assets/", "url(/api/v1/static-preview/" + versionId + "/assets/");
        rewritten = rewritten.replace("\"/favicon", "\"/api/v1/static-preview/" + versionId + "/favicon");
        rewritten = rewritten.replace("'/favicon", "'/api/v1/static-preview/" + versionId + "/favicon");
        rewritten = appendPreviewTokenToStaticUrls(rewritten, previewToken);
        return rewritten;
    }

    private String appendPreviewTokenToStaticUrls(String content, String previewToken) {
        String target = "/api/v1/static-preview/";
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        while (cursor < content.length()) {
            int start = content.indexOf(target, cursor);
            if (start < 0) {
                builder.append(content.substring(cursor));
                break;
            }
            builder.append(content, cursor, start);
            int end = start;
            while (end < content.length()) {
                char current = content.charAt(end);
                if (current == '"' || current == '\'' || Character.isWhitespace(current) || current == ')') {
                    break;
                }
                end++;
            }
            String url = content.substring(start, end);
            if (!url.contains("previewToken=")) {
                url = url + (url.contains("?") ? "&" : "?") + "previewToken=" + previewToken;
            }
            builder.append(url);
            cursor = end;
        }
        return builder.toString();
    }

    public record StaticPreviewFileResponse(
            byte[] content,
            MediaType mediaType
    ) {
    }
}
