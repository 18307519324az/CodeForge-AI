package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AppVersionDetailResponse;
import com.codeforge.ai.application.dto.app.AppVersionDiffFileResponse;
import com.codeforge.ai.application.dto.app.AppVersionDiffResponse;
import com.codeforge.ai.application.dto.app.AppVersionFileContentResponse;
import com.codeforge.ai.application.dto.app.AppVersionFileResponse;
import com.codeforge.ai.application.dto.app.AppVersionListItemResponse;
import com.codeforge.ai.application.dto.app.AppVersionRollbackResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ArtifactSnapshotEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ArtifactSnapshotEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.PageResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppVersionApplicationService {

    private static final String FILE_TREE_SNAPSHOT_TYPE = "FILE_TREE";

    private final AiAppEntityMapper aiAppEntityMapper;
    private final ArtifactSnapshotEntityMapper artifactSnapshotEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;

    public PageResponse<AppVersionListItemResponse> listVersions(CurrentUser currentUser, Long appId, PageRequest request) {
        validatePageRequest(request);
        AiAppEntity appEntity = requireReadableApp(currentUser, appId);
        List<AppVersionListItemResponse> allRecords = appVersionEntityMapper.findByAppId(appEntity.getId()).stream()
                .map(this::toListItemResponse)
                .toList();
        int fromIndex = (int) ((request.getPageNo() - 1) * request.getPageSize());
        if (fromIndex >= allRecords.size()) {
            return PageResponse.<AppVersionListItemResponse>builder()
                    .records(List.of())
                    .pageNo(request.getPageNo())
                    .pageSize(request.getPageSize())
                    .total(allRecords.size())
                    .build();
        }
        int toIndex = Math.min(allRecords.size(), fromIndex + (int) request.getPageSize());
        return PageResponse.<AppVersionListItemResponse>builder()
                .records(allRecords.subList(fromIndex, toIndex))
                .pageNo(request.getPageNo())
                .pageSize(request.getPageSize())
                .total(allRecords.size())
                .build();
    }

    public AppVersionDetailResponse getVersion(CurrentUser currentUser, Long appId, Long versionId) {
        AppVersionEntity versionEntity = requireReadableVersion(currentUser, appId, versionId);
        return toDetailResponse(versionEntity);
    }

    public List<AppVersionFileResponse> listVersionFiles(CurrentUser currentUser, Long appId, Long versionId) {
        AppVersionEntity versionEntity = requireReadableVersion(currentUser, appId, versionId);
        return generatedFileEntityMapper.findByAppVersionId(versionEntity.getId()).stream()
                .map(this::toFileResponse)
                .toList();
    }

    public AppVersionFileContentResponse getFileContent(CurrentUser currentUser, Long appId, Long versionId, String filePath) {
        AppVersionEntity versionEntity = requireReadableVersion(currentUser, appId, versionId);
        GeneratedFileEntity fileEntity = generatedFileEntityMapper.findByAppVersionIdAndFilePath(versionEntity.getId(), filePath);
        if (fileEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本文件不存在");
        }
        if (fileEntity.getFileContent() != null) {
            return new AppVersionFileContentResponse(
                    versionEntity.getId(),
                    fileEntity.getFilePath(),
                    fileEntity.getFileName(),
                    fileEntity.getFileType(),
                    fileEntity.getFileContent()
            );
        }
        if (fileEntity.getStoragePath() == null || fileEntity.getStoragePath().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本文件内容不存在");
        }
        try {
            Path storagePath = Paths.get(fileEntity.getStoragePath()).normalize();
            String content = Files.readString(storagePath, StandardCharsets.UTF_8);
            return new AppVersionFileContentResponse(
                    versionEntity.getId(),
                    fileEntity.getFilePath(),
                    fileEntity.getFileName(),
                    fileEntity.getFileType(),
                    content
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本文件内容不存在");
        }
    }

    public AppVersionDiffResponse diffVersions(CurrentUser currentUser, Long appId, Long fromVersionId, Long toVersionId) {
        AppVersionEntity fromVersion = requireReadableVersion(currentUser, appId, fromVersionId);
        AppVersionEntity toVersion = requireReadableVersion(currentUser, appId, toVersionId);
        ArtifactSnapshotEntity fromSnapshot = artifactSnapshotEntityMapper.findLatestByAppVersionIdAndSnapshotType(
                fromVersion.getId(), FILE_TREE_SNAPSHOT_TYPE);
        ArtifactSnapshotEntity toSnapshot = artifactSnapshotEntityMapper.findLatestByAppVersionIdAndSnapshotType(
                toVersion.getId(), FILE_TREE_SNAPSHOT_TYPE);
        Map<String, GeneratedFileEntity> fromFiles = toFileMap(generatedFileEntityMapper.findByAppVersionId(fromVersion.getId()));
        Map<String, GeneratedFileEntity> toFiles = toFileMap(generatedFileEntityMapper.findByAppVersionId(toVersion.getId()));

        TreeSet<String> allPaths = new TreeSet<>();
        allPaths.addAll(fromFiles.keySet());
        allPaths.addAll(toFiles.keySet());
        List<AppVersionDiffFileResponse> changedFiles = allPaths.stream()
                .map(path -> toDiffFileResponse(path, fromFiles.get(path), toFiles.get(path)))
                .filter(response -> response != null)
                .sorted(Comparator.comparing(AppVersionDiffFileResponse::filePath))
                .toList();

        return new AppVersionDiffResponse(
                appId,
                fromVersion.getId(),
                fromVersion.getVersionNo(),
                fromSnapshot == null ? null : fromSnapshot.getContentHash(),
                toVersion.getId(),
                toVersion.getVersionNo(),
                toSnapshot == null ? null : toSnapshot.getContentHash(),
                changedFiles
        );
    }

    @Transactional
    public AppVersionRollbackResponse rollbackVersion(CurrentUser currentUser, Long appId, Long versionId) {
        AiAppEntity appEntity = requireEditableApp(currentUser, appId);
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appEntity.getId(), versionId);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "App version does not exist");
        }
        aiAppEntityMapper.updateCurrentVersionId(appEntity.getId(), versionEntity.getId(), currentUser.requiredUserId());
        return new AppVersionRollbackResponse(appId, versionEntity.getId(), versionEntity.getVersionNo(), "ROLLED_BACK");
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

    private AppVersionEntity requireReadableVersion(CurrentUser currentUser, Long appId, Long versionId) {
        AiAppEntity appEntity = requireReadableApp(currentUser, appId);
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appEntity.getId(), versionId);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应用版本不存在");
        }
        return versionEntity;
    }

    private void validatePageRequest(PageRequest request) {
        if (request.getPageNo() <= 0 || request.getPageSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNo/pageSize 非法");
        }
    }

    private AppVersionListItemResponse toListItemResponse(AppVersionEntity entity) {
        return new AppVersionListItemResponse(
                entity.getId(),
                entity.getVersionNo(),
                entity.getVersionSource(),
                entity.getSourceTaskId(),
                entity.getChangeSummary(),
                entity.getStatus(),
                entity.getPublishedAt(),
                entity.getCreatedAt()
        );
    }

    private AppVersionDetailResponse toDetailResponse(AppVersionEntity entity) {
        return new AppVersionDetailResponse(
                entity.getId(),
                entity.getAppId(),
                entity.getVersionNo(),
                entity.getVersionSource(),
                entity.getSourceTaskId(),
                entity.getChangeSummary(),
                entity.getStatus(),
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AppVersionFileResponse toFileResponse(GeneratedFileEntity entity) {
        return new AppVersionFileResponse(
                entity.getId(),
                entity.getAppVersionId(),
                entity.getFilePath(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getContentHash(),
                entity.getFileSize()
        );
    }

    private Map<String, GeneratedFileEntity> toFileMap(List<GeneratedFileEntity> files) {
        return files.stream().collect(java.util.stream.Collectors.toMap(
                GeneratedFileEntity::getFilePath,
                entity -> entity,
                (left, right) -> right
        ));
    }

    private AppVersionDiffFileResponse toDiffFileResponse(String filePath,
                                                          GeneratedFileEntity fromFile,
                                                          GeneratedFileEntity toFile) {
        if (fromFile == null && toFile == null) {
            return null;
        }
        if (fromFile == null) {
            return new AppVersionDiffFileResponse(
                    filePath,
                    "ADDED",
                    null,
                    toFile.getContentHash(),
                    null,
                    toFile.getFileSize()
            );
        }
        if (toFile == null) {
            return new AppVersionDiffFileResponse(
                    filePath,
                    "REMOVED",
                    fromFile.getContentHash(),
                    null,
                    fromFile.getFileSize(),
                    null
            );
        }
        if (java.util.Objects.equals(fromFile.getContentHash(), toFile.getContentHash())
                && java.util.Objects.equals(fromFile.getFileSize(), toFile.getFileSize())) {
            return null;
        }
        return new AppVersionDiffFileResponse(
                filePath,
                "MODIFIED",
                fromFile.getContentHash(),
                toFile.getContentHash(),
                fromFile.getFileSize(),
                toFile.getFileSize()
        );
    }
}
