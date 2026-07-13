package com.codeforge.ai.application.service;

import cn.hutool.core.util.IdUtil;
import com.codeforge.ai.application.dto.app.AiAppCreateRequest;
import com.codeforge.ai.application.dto.app.AiAppDetailResponse;
import com.codeforge.ai.application.dto.app.AiAppListItemResponse;
import com.codeforge.ai.application.dto.app.AiAppQueryRequest;
import com.codeforge.ai.application.dto.app.AiAppUpdateRequest;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.enums.AiAppStatus;
import com.codeforge.ai.domain.app.enums.AiAppVisibility;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiAppApplicationService {

    private final AiAppEntityMapper aiAppEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final AppListSummaryAggregator appListSummaryAggregator;
    private final AppPublicationApplicationService appPublicationApplicationService;

    @Transactional
    public AiAppDetailResponse createApp(CurrentUser currentUser, AiAppCreateRequest request) {
        workspaceAccessService.requireEditorAccess(currentUser, request.getWorkspaceId());
        LocalDateTime now = LocalDateTime.now();
        AiAppEntity entity = AiAppEntity.builder()
                .id(IdUtil.getSnowflakeNextId())
                .workspaceId(request.getWorkspaceId())
                .name(request.getName())
                .description(request.getDescription())
                .appType(request.getAppType())
                .status(AiAppStatus.DRAFT.name())
                .visibility(AiAppVisibility.PRIVATE.name())
                .build();
        entity.setCreatedBy(currentUser.requiredUserId());
        entity.setUpdatedBy(currentUser.requiredUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        aiAppEntityMapper.insertApp(entity);
        return toDetailResponse(entity, resolveSummary(entity));
    }

    public PageResponse<AiAppListItemResponse> listApps(CurrentUser currentUser, AiAppQueryRequest request) {
        AppListPaginationSupport.normalize(request);
        validatePageRequest(request);
        List<Long> workspaceIds = resolveWorkspaceIds(currentUser, request.getWorkspaceId());
        if (workspaceIds.isEmpty()) {
            return PageResponse.<AiAppListItemResponse>builder()
                    .records(List.of())
                    .pageNo(request.getPageNo())
                    .pageSize(request.getPageSize())
                    .total(0)
                    .build();
        }
        long total = aiAppEntityMapper.countAccessibleApps(
                workspaceIds, request.getKeyword(), request.getStatus(), request.getAppType());
        long pageNo = request.getPageNo();
        long totalPages = AppListPaginationSupport.totalPages(total, request.getPageSize());
        if (totalPages > 0 && pageNo > totalPages) {
            pageNo = totalPages;
            request.setPageNo(pageNo);
        }
        long offset = AppListPaginationSupport.offset(request);
        List<AiAppEntity> entities = aiAppEntityMapper.findAccessibleAppsPage(
                workspaceIds,
                request.getKeyword(),
                request.getStatus(),
                request.getAppType(),
                offset,
                request.getPageSize());
        Map<Long, AppListItemSummary> summaries = appListSummaryAggregator.aggregate(entities);
        List<AiAppListItemResponse> records = entities.stream()
                .map(entity -> toListItemResponse(entity, summaries.get(entity.getId())))
                .toList();
        return PageResponse.<AiAppListItemResponse>builder()
                .records(records)
                .pageNo(pageNo)
                .pageSize(request.getPageSize())
                .total(total)
                .build();
    }

    public AiAppDetailResponse getApp(CurrentUser currentUser, Long appId) {
        AiAppEntity entity = requireReadableApp(currentUser, appId);
        return toDetailResponse(entity, resolveSummary(entity));
    }

    @Transactional
    public AiAppDetailResponse updateApp(CurrentUser currentUser, Long appId, AiAppUpdateRequest request) {
        AiAppEntity existing = requireEditableApp(currentUser, appId);
        AiAppEntity updateEntity = AiAppEntity.builder()
                .id(existing.getId())
                .name(StringUtils.hasText(request.getName()) ? request.getName() : existing.getName())
                .description(request.getDescription())
                .coverUrl(request.getCoverUrl())
                .visibility(normalizeVisibility(request.getVisibility(), existing.getVisibility()))
                .build();
        updateEntity.setUpdatedBy(currentUser.requiredUserId());
        aiAppEntityMapper.updateApp(updateEntity);
        AiAppEntity refreshed = aiAppEntityMapper.selectOneById(appId);
        return toDetailResponse(refreshed, resolveSummary(refreshed));
    }

    @Transactional
    public AiAppDetailResponse archiveApp(CurrentUser currentUser, Long appId) {
        AiAppEntity existing = requireAdminApp(currentUser, appId);
        appPublicationApplicationService.syncPublicationOnAppArchive(currentUser, appId);
        aiAppEntityMapper.updateStatus(existing.getId(), AiAppStatus.ARCHIVED.name(), currentUser.requiredUserId());
        AiAppEntity refreshed = aiAppEntityMapper.selectOneById(appId);
        return toDetailResponse(refreshed, resolveSummary(refreshed));
    }

    @Transactional
    public void deleteApp(CurrentUser currentUser, Long appId) {
        AiAppEntity existing = requireAdminApp(currentUser, appId);
        aiAppEntityMapper.softDelete(existing.getId(), currentUser.requiredUserId());
    }

    private AiAppEntity requireReadableApp(CurrentUser currentUser, Long appId) {
        AiAppEntity entity = aiAppEntityMapper.selectOneById(appId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireReadAccess(currentUser, entity.getWorkspaceId());
        return entity;
    }

    private AiAppEntity requireEditableApp(CurrentUser currentUser, Long appId) {
        AiAppEntity entity = aiAppEntityMapper.selectOneById(appId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireEditorAccess(currentUser, entity.getWorkspaceId());
        return entity;
    }

    private AiAppEntity requireAdminApp(CurrentUser currentUser, Long appId) {
        AiAppEntity entity = aiAppEntityMapper.selectOneById(appId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireAdminAccess(currentUser, entity.getWorkspaceId());
        return entity;
    }

    private List<Long> resolveWorkspaceIds(CurrentUser currentUser, Long workspaceId) {
        if (workspaceId != null) {
            workspaceAccessService.requireReadAccess(currentUser, workspaceId);
            return List.of(workspaceId);
        }
        return workspaceAccessService.listReadableWorkspaceIds(currentUser);
    }

    private void validatePageRequest(PageRequest pageRequest) {
        if (pageRequest.getPageNo() <= 0 || pageRequest.getPageSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNo/pageSize 非法");
        }
    }

    private String normalizeVisibility(String visibility, String fallback) {
        if (!StringUtils.hasText(visibility)) {
            return fallback;
        }
        try {
            return AiAppVisibility.valueOf(visibility).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "visibility 不合法");
        }
    }

    private AiAppListItemResponse toListItemResponse(AiAppEntity entity, AppListItemSummary summary) {
        AppListItemSummary safeSummary = summary == null
                ? AppListItemSummary.builder().displayStatus(AppDisplayStatusDeriver.derive(entity, false, null, null, "NONE")).build()
                : summary;
        return new AiAppListItemResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCoverUrl(),
                entity.getAppType(),
                entity.getStatus(),
                entity.getVisibility(),
                entity.getCurrentVersionId(),
                entity.getLatestTaskId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                safeSummary.getCurrentVersionNo(),
                safeSummary.getLatestGenerationSource(),
                safeSummary.getGeneratedFileCount(),
                safeSummary.getLatestExportStatus(),
                safeSummary.getDisplayStatus(),
                safeSummary.getPublicationStatus(),
                safeSummary.getPublicationSlug(),
                safeSummary.getPublicationId()
        );
    }

    private AppListItemSummary resolveSummary(AiAppEntity entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return appListSummaryAggregator.aggregate(List.of(entity)).get(entity.getId());
    }

    private AiAppDetailResponse toDetailResponse(AiAppEntity entity, AppListItemSummary summary) {
        AppListItemSummary safeSummary = summary == null
                ? AppListItemSummary.builder().displayStatus(AppDisplayStatusDeriver.derive(entity, false, null, null, "NONE")).build()
                : summary;
        return new AiAppDetailResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCoverUrl(),
                entity.getAppType(),
                entity.getStatus(),
                entity.getVisibility(),
                entity.getCurrentVersionId(),
                entity.getLatestTaskId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                safeSummary.getCurrentVersionNo(),
                safeSummary.getLatestGenerationSource(),
                safeSummary.getGeneratedFileCount(),
                safeSummary.getLatestExportStatus(),
                safeSummary.getDisplayStatus(),
                safeSummary.getPublicationStatus(),
                safeSummary.getPublicationSlug(),
                safeSummary.getPublicationId()
        );
    }
}
