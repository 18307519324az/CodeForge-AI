package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AppVersionPreviewTokenResponse;
import com.codeforge.ai.application.dto.publication.PublicAppDetailResponse;
import com.codeforge.ai.application.dto.publication.PublicAppListItemResponse;
import com.codeforge.ai.application.dto.publication.PublicAppQueryRequest;
import com.codeforge.ai.application.dto.publication.PublicDownloadTokenResponse;
import com.codeforge.ai.application.dto.publication.PublicAppViewResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.domain.app.enums.PublicationDownloadAvailability;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppPublicationEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.projection.VersionExportStatusRow;
import com.codeforge.ai.infrastructure.persistence.mapper.PublicationViewDedupeEntityMapper;
import com.codeforge.ai.infrastructure.security.DownloadAccessTokenService;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.infrastructure.security.PublicationViewerIdentityService;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.response.PageResponse;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicAppApplicationService {

    private static final long DEFAULT_PAGE_SIZE = 12L;
    private static final long MAX_PAGE_SIZE = 50L;

    private final AppPublicationEntityMapper appPublicationEntityMapper;
    private final AppPublicationApplicationService appPublicationApplicationService;
    private final AiAppEntityMapper aiAppEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final UserEntityMapper userEntityMapper;
    private final ExportPackageEntityMapper exportPackageEntityMapper;
    private final PreviewAccessTokenService previewAccessTokenService;
    private final DownloadAccessTokenService downloadAccessTokenService;
    private final PublicationViewDedupeEntityMapper publicationViewDedupeEntityMapper;
    private final PublicationViewerIdentityService publicationViewerIdentityService;

    public PageResponse<PublicAppListItemResponse> listPublishedApps(PublicAppQueryRequest request) {
        long pageNo = request.pageNo() == null || request.pageNo() < 1 ? 1L : request.pageNo();
        long pageSize = request.pageSize() == null || request.pageSize() < 1 ? DEFAULT_PAGE_SIZE : request.pageSize();
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        String sort = request.sort() == null || request.sort().isBlank() ? "LATEST" : request.sort().trim().toUpperCase();
        if (!"POPULAR".equals(sort)) {
            sort = "LATEST";
        }

        long total = appPublicationEntityMapper.countPublished(request.keyword(), request.appType());
        if (total == 0) {
            return PageResponse.<PublicAppListItemResponse>builder()
                    .records(List.of())
                    .pageNo(pageNo)
                    .pageSize(pageSize)
                    .total(0L)
                    .build();
        }

        long offset = (pageNo - 1) * pageSize;
        List<AppPublicationEntity> publications = appPublicationEntityMapper.findPublishedPage(
                request.keyword(), request.appType(), sort, pageSize, offset);
        if (publications.isEmpty()) {
            return PageResponse.<PublicAppListItemResponse>builder()
                    .records(List.of())
                    .pageNo(pageNo)
                    .pageSize(pageSize)
                    .total(total)
                    .build();
        }

        Map<Long, AiAppEntity> appsById = loadApps(publications);
        Map<Long, AppVersionEntity> versionsById = loadVersions(publications);
        Map<Long, UserEntity> usersById = loadPublishers(publications);
        Map<Long, String> exportStatusByVersionId = loadExportStatuses(publications);

        List<PublicAppListItemResponse> records = publications.stream()
                .map(publication -> toListItem(
                        publication, appsById, versionsById, usersById, exportStatusByVersionId))
                .toList();

        return PageResponse.<PublicAppListItemResponse>builder()
                .records(records)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .build();
    }

    public PublicAppDetailResponse getPublishedAppDetail(String slug) {
        AppPublicationEntity publication = appPublicationApplicationService.requirePublishedBySlug(slug);
        return toDetail(publication);
    }

    @Transactional
    public PublicAppViewResponse recordPublicAppView(String slug,
                                                     Long userId,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        AppPublicationEntity publication = appPublicationApplicationService.requirePublishedBySlug(slug);
        String viewerKey = publicationViewerIdentityService.resolveViewerKey(userId, request, response);
        String viewerKeyHash = publicationViewerIdentityService.hashViewerKey(viewerKey);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(24);

        boolean counted = false;
        if (publicationViewDedupeEntityMapper.insertIgnore(publication.getId(), viewerKeyHash, now) > 0) {
            counted = true;
        } else if (publicationViewDedupeEntityMapper.updateIfExpired(
                publication.getId(), viewerKeyHash, cutoff, now) > 0) {
            counted = true;
        }

        if (counted) {
            appPublicationEntityMapper.incrementViewCount(publication.getId());
        }

        AppPublicationEntity refreshed = appPublicationEntityMapper.findActiveById(publication.getId());
        long viewCount = refreshed == null || refreshed.getViewCount() == null ? 0L : refreshed.getViewCount();
        return new PublicAppViewResponse(counted, viewCount);
    }

    public AppVersionPreviewTokenResponse issuePublicPreviewToken(String slug) {
        AppPublicationEntity publication = appPublicationApplicationService.requirePublishedBySlug(slug);
        if (!Boolean.TRUE.equals(publication.getAllowPreview())) {
            throw new BusinessException(ErrorCode.PUBLICATION_PREVIEW_DISABLED);
        }
        String previewToken = previewAccessTokenService.createPublicPreviewToken(
                publication.getId(), publication.getAppId(), publication.getVersionId());
        return new AppVersionPreviewTokenResponse(
                "/api/v1/static-preview/" + publication.getVersionId()
                        + "/index.html?previewToken=" + previewToken,
                previewAccessTokenService.getPreviewTokenExpireSeconds()
        );
    }

    public PublicDownloadTokenResponse issuePublicDownloadToken(String slug) {
        AppPublicationEntity publication = appPublicationApplicationService.requirePublishedBySlug(slug);
        if (!Boolean.TRUE.equals(publication.getAllowDownload())) {
            throw new BusinessException(ErrorCode.PUBLICATION_DOWNLOAD_DISABLED);
        }
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(
                publication.getAppId(), publication.getVersionId());
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "发布版本与应用不匹配");
        }
        ExportPackageEntity exportPackage = exportPackageEntityMapper.findLatestReadyByAppVersionId(publication.getVersionId());
        if (exportPackage == null) {
            throw new BusinessException(ErrorCode.PUBLICATION_EXPORT_NOT_READY);
        }
        String downloadToken = downloadAccessTokenService.createDownloadToken(
                publication.getId(),
                publication.getAppId(),
                publication.getVersionId(),
                exportPackage.getId());
        return new PublicDownloadTokenResponse(
                "/api/v1/public/downloads/file?downloadToken=" + downloadToken,
                downloadAccessTokenService.getDownloadTokenExpireSeconds()
        );
    }

    private PublicAppDetailResponse toDetail(AppPublicationEntity publication) {
        AiAppEntity app = aiAppEntityMapper.selectOneById(publication.getAppId());
        AppVersionEntity version = appVersionEntityMapper.findByAppIdAndVersionId(
                publication.getAppId(), publication.getVersionId());
        UserEntity publisher = userEntityMapper.findById(publication.getPublisherUserId());
        String exportStatus = resolveExportStatus(publication.getVersionId());
        PublicationDownloadAvailability downloadAvailability = PublicationDownloadAvailabilityDeriver.derive(
                publication.getAllowDownload(), exportStatus);
        return new PublicAppDetailResponse(
                publication.getId(),
                publication.getSlug(),
                publication.getPublicTitle(),
                publication.getPublicDescription(),
                app == null ? null : app.getAppType(),
                resolvePublisherName(publisher),
                version == null ? null : version.getVersionNo(),
                version == null ? null : version.getVersionSource(),
                publication.getAllowPreview(),
                publication.getAllowDownload(),
                downloadAvailability,
                publication.getPublishedAt(),
                publication.getUpdatedAt(),
                publication.getViewCount(),
                publication.getDownloadCount()
        );
    }

    private PublicAppListItemResponse toListItem(AppPublicationEntity publication,
                                                 Map<Long, AiAppEntity> appsById,
                                                 Map<Long, AppVersionEntity> versionsById,
                                                 Map<Long, UserEntity> usersById,
                                                 Map<Long, String> exportStatusByVersionId) {
        AiAppEntity app = appsById.get(publication.getAppId());
        AppVersionEntity version = versionsById.get(publication.getVersionId());
        UserEntity publisher = usersById.get(publication.getPublisherUserId());
        String exportStatus = exportStatusByVersionId.get(publication.getVersionId());
        PublicationDownloadAvailability downloadAvailability = PublicationDownloadAvailabilityDeriver.derive(
                publication.getAllowDownload(), exportStatus);
        return new PublicAppListItemResponse(
                publication.getId(),
                publication.getSlug(),
                publication.getPublicTitle(),
                publication.getPublicDescription(),
                app == null ? null : app.getAppType(),
                resolvePublisherName(publisher),
                version == null ? null : version.getVersionNo(),
                publication.getAllowPreview(),
                publication.getAllowDownload(),
                downloadAvailability,
                publication.getPublishedAt(),
                publication.getViewCount(),
                publication.getDownloadCount()
        );
    }

    private Map<Long, AiAppEntity> loadApps(List<AppPublicationEntity> publications) {
        List<Long> appIds = publications.stream().map(AppPublicationEntity::getAppId).filter(Objects::nonNull).distinct().toList();
        if (appIds.isEmpty()) {
            return Map.of();
        }
        return aiAppEntityMapper.findByIds(appIds).stream()
                .collect(Collectors.toMap(AiAppEntity::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, AppVersionEntity> loadVersions(List<AppPublicationEntity> publications) {
        List<Long> versionIds = publications.stream()
                .map(AppPublicationEntity::getVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return appVersionEntityMapper.findByIds(versionIds).stream()
                .collect(Collectors.toMap(AppVersionEntity::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, UserEntity> loadPublishers(List<AppPublicationEntity> publications) {
        List<Long> userIds = publications.stream()
                .map(AppPublicationEntity::getPublisherUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userEntityMapper.findByIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, String> loadExportStatuses(List<AppPublicationEntity> publications) {
        List<Long> versionIds = publications.stream()
                .map(AppPublicationEntity::getVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> statuses = new HashMap<>();
        for (VersionExportStatusRow row : exportPackageEntityMapper.findLatestStatusByVersionIds(versionIds)) {
            statuses.put(row.getAppVersionId(), row.getStatus());
        }
        return statuses;
    }

    private String resolveExportStatus(Long versionId) {
        if (versionId == null) {
            return null;
        }
        List<VersionExportStatusRow> rows = exportPackageEntityMapper.findLatestStatusByVersionIds(List.of(versionId));
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0).getStatus();
    }

    private static String resolvePublisherName(UserEntity publisher) {
        if (publisher == null) {
            return "匿名用户";
        }
        if (publisher.getDisplayName() != null && !publisher.getDisplayName().isBlank()) {
            return publisher.getDisplayName();
        }
        if (publisher.getAccount() != null && !publisher.getAccount().isBlank()) {
            return publisher.getAccount();
        }
        return "匿名用户";
    }
}
