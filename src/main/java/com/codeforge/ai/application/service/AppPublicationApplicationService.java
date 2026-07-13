package com.codeforge.ai.application.service;

import cn.hutool.core.util.IdUtil;
import com.codeforge.ai.application.dto.publication.AppPublicationCreateRequest;
import com.codeforge.ai.application.dto.publication.AppPublicationResponse;
import com.codeforge.ai.application.dto.publication.AppPublicationUpdateRequest;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.app.enums.AppPublicationStatus;
import com.codeforge.ai.domain.common.BaseEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppPublicationApplicationService {

    private static final String ENTRY_FILE = "index.html";

    private final AiAppEntityMapper aiAppEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final AppPublicationEntityMapper appPublicationEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final ExportPackageEntityMapper exportPackageEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final VueProjectBuildService vueProjectBuildService;
    private final MarketplacePublicationAccessGuard marketplacePublicationAccessGuard;
    private final MarketplaceAuditService marketplaceAuditService;

    @Transactional
    public AppPublicationResponse publishApp(CurrentUser currentUser, Long appId, @Valid AppPublicationCreateRequest request) {
        AiAppEntity appEntity = requirePublishableApp(currentUser, appId);
        AppVersionEntity versionEntity = requirePublishableVersion(appEntity, request.versionId());
        validatePublicationArtifacts(versionEntity, request.allowPreview(), request.allowDownload());

        AppPublicationEntity existing = appPublicationEntityMapper.findByAppId(appId);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AppPublicationEntity entity = AppPublicationEntity.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .appId(appId)
                    .versionId(versionEntity.getId())
                    .publisherUserId(currentUser.requiredUserId())
                    .publicTitle(request.publicTitle())
                    .publicDescription(request.publicDescription())
                    .slug(resolveUniqueSlug(request.publicTitle()))
                    .status(AppPublicationStatus.PUBLISHED)
                    .allowPreview(request.allowPreview())
                    .allowDownload(request.allowDownload())
                    .publishedAt(now)
                    .unpublishedAt(null)
                    .viewCount(0L)
                    .downloadCount(0L)
                    .build();
            applyAuditFields(entity, currentUser.requiredUserId());
            appPublicationEntityMapper.insert(entity);
            marketplaceAuditService.recordPublish(currentUser.requiredUserId(), entity);
            return toResponse(entity, versionEntity.getVersionNo());
        }

        if (AppPublicationStatus.PUBLISHED.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.PUBLICATION_ALREADY_PUBLISHED);
        }

        existing.setVersionId(versionEntity.getId());
        existing.setPublisherUserId(currentUser.requiredUserId());
        existing.setPublicTitle(request.publicTitle());
        existing.setPublicDescription(request.publicDescription());
        existing.setStatus(AppPublicationStatus.PUBLISHED);
        existing.setAllowPreview(request.allowPreview());
        existing.setAllowDownload(request.allowDownload());
        existing.setPublishedAt(now);
        existing.setUnpublishedAt(null);
        existing.setUpdatedBy(currentUser.requiredUserId());
        existing.setUpdatedAt(now);
        appPublicationEntityMapper.update(existing);
        marketplaceAuditService.recordPublish(currentUser.requiredUserId(), existing);
        return toResponse(existing, versionEntity.getVersionNo());
    }

    @Transactional
    public AppPublicationResponse updatePublication(CurrentUser currentUser,
                                                      Long appId,
                                                      Long publicationId,
                                                      @Valid AppPublicationUpdateRequest request) {
        AiAppEntity appEntity = requirePublishableApp(currentUser, appId);
        AppPublicationEntity publication = requireOwnedPublication(appEntity.getId(), publicationId);

        AppVersionEntity versionEntity = publication.getVersionId() == null
                ? null
                : appVersionEntityMapper.findByAppIdAndVersionId(appId, publication.getVersionId());
        boolean versionChanged = false;
        if (request.versionId() != null) {
            versionEntity = requirePublishableVersion(appEntity, request.versionId());
            versionChanged = !request.versionId().equals(publication.getVersionId());
            publication.setVersionId(versionEntity.getId());
        }
        if (request.publicTitle() != null) {
            publication.setPublicTitle(request.publicTitle());
        }
        if (request.publicDescription() != null) {
            publication.setPublicDescription(request.publicDescription());
        }
        if (request.allowPreview() != null) {
            publication.setAllowPreview(request.allowPreview());
        }
        if (request.allowDownload() != null) {
            publication.setAllowDownload(request.allowDownload());
        }

        boolean allowPreview = Boolean.TRUE.equals(publication.getAllowPreview());
        boolean allowDownload = Boolean.TRUE.equals(publication.getAllowDownload());
        validatePublicationArtifacts(versionEntity, allowPreview, allowDownload);

        publication.setUpdatedBy(currentUser.requiredUserId());
        publication.setUpdatedAt(LocalDateTime.now());
        appPublicationEntityMapper.update(publication);
        if (versionChanged) {
            marketplaceAuditService.recordRepublish(currentUser.requiredUserId(), publication);
        }
        return toResponse(publication, versionEntity == null ? null : versionEntity.getVersionNo());
    }

    @Transactional
    public AppPublicationResponse unpublishApp(CurrentUser currentUser, Long appId, Long publicationId) {
        AiAppEntity appEntity = requirePublishableApp(currentUser, appId);
        AppPublicationEntity publication = requireOwnedPublication(appEntity.getId(), publicationId);
        if (!AppPublicationStatus.PUBLISHED.equals(publication.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "应用当前未处于发布状态");
        }
        publication.setStatus(AppPublicationStatus.UNPUBLISHED);
        publication.setUnpublishedAt(LocalDateTime.now());
        publication.setUpdatedBy(currentUser.requiredUserId());
        publication.setUpdatedAt(LocalDateTime.now());
        appPublicationEntityMapper.update(publication);
        marketplaceAuditService.recordUnpublish(currentUser.requiredUserId(), publication);

        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appId, publication.getVersionId());
        return toResponse(publication, versionEntity == null ? null : versionEntity.getVersionNo());
    }

    @Transactional
    public void syncPublicationOnAppArchive(CurrentUser currentUser, Long appId) {
        AppPublicationEntity publication = appPublicationEntityMapper.findByAppId(appId);
        if (publication == null || !AppPublicationStatus.PUBLISHED.equals(publication.getStatus())) {
            return;
        }
        publication.setStatus(AppPublicationStatus.UNPUBLISHED);
        publication.setUnpublishedAt(LocalDateTime.now());
        publication.setUpdatedBy(currentUser.requiredUserId());
        publication.setUpdatedAt(LocalDateTime.now());
        appPublicationEntityMapper.update(publication);
        marketplaceAuditService.recordArchive(currentUser.requiredUserId(), publication);
    }

    public AppPublicationResponse getPublicationForOwner(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = requireReadableApp(currentUser, appId);
        AppPublicationEntity publication = appPublicationEntityMapper.findByAppId(appEntity.getId());
        if (publication == null) {
            throw new BusinessException(ErrorCode.PUBLICATION_NOT_FOUND);
        }
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appId, publication.getVersionId());
        return toResponse(publication, versionEntity == null ? null : versionEntity.getVersionNo());
    }

    public Optional<AppPublicationEntity> findByAppId(Long appId) {
        return Optional.ofNullable(appPublicationEntityMapper.findByAppId(appId));
    }

    public AppPublicationEntity requirePublishedBySlug(String slug) {
        AppPublicationEntity publication = appPublicationEntityMapper.findBySlug(slug);
        return marketplacePublicationAccessGuard.requirePubliclyAccessible(publication);
    }

    private AiAppEntity requireReadableApp(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireReadAccess(currentUser, appEntity.getWorkspaceId());
        return appEntity;
    }

    private AiAppEntity requirePublishableApp(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.PUBLICATION_APP_NOT_FOUND);
        }
        if ("ARCHIVED".equals(appEntity.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "已归档应用无法发布");
        }
        workspaceAccessService.requireEditorAccess(currentUser, appEntity.getWorkspaceId());
        return appEntity;
    }

    private AppVersionEntity requirePublishableVersion(AiAppEntity appEntity, Long versionId) {
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appEntity.getId(), versionId);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.PUBLICATION_VERSION_NOT_FOUND);
        }
        if (!appEntity.getId().equals(versionEntity.getAppId())) {
            throw new BusinessException(ErrorCode.PUBLICATION_VERSION_NOT_OWNED);
        }
        return versionEntity;
    }

    private AppPublicationEntity requireOwnedPublication(Long appId, Long publicationId) {
        AppPublicationEntity publication = appPublicationEntityMapper.findActiveById(publicationId);
        if (publication == null || !appId.equals(publication.getAppId())) {
            throw new BusinessException(ErrorCode.PUBLICATION_NOT_FOUND);
        }
        return publication;
    }

    private void validatePublicationArtifacts(AppVersionEntity versionEntity,
                                              boolean allowPreview,
                                              boolean allowDownload) {
        List<GeneratedFileEntity> generatedFiles = generatedFileEntityMapper.findByAppVersionId(versionEntity.getId());
        if (generatedFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.PUBLICATION_ARTIFACT_NOT_READY);
        }
        boolean hasEntry = generatedFiles.stream()
                .anyMatch(file -> ENTRY_FILE.equalsIgnoreCase(normalizePath(file.getFilePath())))
                || vueProjectBuildService.serveBuiltFile(versionEntity.getId(), ENTRY_FILE).isPresent();
        if (!hasEntry) {
            throw new BusinessException(ErrorCode.PUBLICATION_ENTRY_MISSING);
        }
        if (allowDownload && !hasReadyExport(versionEntity.getId())) {
            throw new BusinessException(ErrorCode.PUBLICATION_EXPORT_NOT_READY);
        }
        if (allowPreview && !hasEntry) {
            throw new BusinessException(ErrorCode.PUBLICATION_ENTRY_MISSING);
        }
    }

    private boolean hasReadyExport(Long versionId) {
        ExportPackageEntity exportPackage = exportPackageEntityMapper.findLatestReadyByAppVersionId(versionId);
        return exportPackage != null && "READY".equals(exportPackage.getStatus());
    }

    private String resolveUniqueSlug(String publicTitle) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String slug = PublicationSlugGenerator.generateSlug(publicTitle);
            if (appPublicationEntityMapper.findBySlug(slug) == null) {
                return slug;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法生成唯一 slug");
    }

    private static String normalizePath(String filePath) {
        if (filePath == null) {
            return "";
        }
        String normalized = filePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private AppPublicationResponse toResponse(AppPublicationEntity entity, Integer versionNo) {
        return new AppPublicationResponse(
                entity.getId(),
                entity.getAppId(),
                entity.getVersionId(),
                versionNo,
                entity.getSlug(),
                entity.getStatus(),
                entity.getPublicTitle(),
                entity.getPublicDescription(),
                entity.getAllowPreview(),
                entity.getAllowDownload(),
                entity.getPublishedAt(),
                entity.getUnpublishedAt(),
                entity.getViewCount(),
                entity.getDownloadCount()
        );
    }

    private void applyAuditFields(BaseEntity entity, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        if (entity.getIsDeleted() == null) {
            entity.setIsDeleted(0);
        }
    }
}
