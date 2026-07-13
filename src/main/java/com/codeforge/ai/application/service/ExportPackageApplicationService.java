package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.export.ExportPackageCreateRequest;
import com.codeforge.ai.application.dto.export.ExportPackageCreateResponse;
import com.codeforge.ai.application.dto.export.ExportPackageListItemResponse;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.ExportPackageEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.app.enums.ExportPackageType;
import com.codeforge.ai.domain.common.BaseEntity;
import com.codeforge.ai.shared.util.ExportPackagePathSupport;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.util.ZipEntryPathSupport;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExportPackageApplicationService {

    private final AiAppEntityMapper aiAppEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final ExportPackageEntityMapper exportPackageEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;

    @Value("${codeforge.storage.export-root:generated-exports}")
    private String exportRoot;

    @Transactional
    public ExportPackageCreateResponse createExportPackage(CurrentUser currentUser, @Valid ExportPackageCreateRequest request) {
        AiAppEntity appEntity = requireEditableApp(currentUser, request.getAppId());
        AppVersionEntity versionEntity = requireVersion(appEntity.getId(), request.getAppVersionId());
        List<GeneratedFileEntity> generatedFiles = generatedFileEntityMapper.findByAppVersionId(versionEntity.getId());
        if (generatedFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "No generated files exist for this version");
        }

        ExportPackageType packageType = ExportPackageType.fromValue(request.getPackageType());
        LocalDateTime now = LocalDateTime.now();
        Path resolvedExportRoot = ExportPackagePathSupport.resolveExportRoot(exportRoot);
        Path packagePath = ExportPackagePathSupport.resolvePackagePath(
                resolvedExportRoot, appEntity.getId(), versionEntity.getVersionNo(), packageType, now);
        writeZipPackage(packagePath, generatedFiles);

        ExportPackageEntity exportPackageEntity = ExportPackageEntity.builder()
                .appId(appEntity.getId())
                .appVersionId(versionEntity.getId())
                .packageType(packageType.code())
                .storagePath(packagePath.toString())
                .status("READY")
                .build();
        applyAuditFields(exportPackageEntity, currentUser.requiredUserId());
        exportPackageEntityMapper.insert(exportPackageEntity);

        return new ExportPackageCreateResponse(
                exportPackageEntity.getId(),
                exportPackageEntity.getAppId(),
                exportPackageEntity.getAppVersionId(),
                versionEntity.getVersionNo(),
                exportPackageEntity.getPackageType(),
                exportPackageEntity.getStatus(),
                packagePath.getFileName().toString(),
                exportPackageEntity.getCreatedAt()
        );
    }

    public List<ExportPackageListItemResponse> listExportPackages(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = requireReadableApp(currentUser, appId);
        return exportPackageEntityMapper.findByAppId(appEntity.getId()).stream()
                .map(entity -> new ExportPackageListItemResponse(
                        entity.getId(),
                        entity.getAppId(),
                        entity.getAppVersionId(),
                        entity.getPackageType(),
                        entity.getStatus(),
                        resolveFileName(entity.getStoragePath()),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    private AiAppEntity requireReadableApp(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireReadAccess(currentUser, appEntity.getWorkspaceId());
        return appEntity;
    }

    private AiAppEntity requireEditableApp(CurrentUser currentUser, Long appId) {
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireEditorAccess(currentUser, appEntity.getWorkspaceId());
        return appEntity;
    }

    private AppVersionEntity requireVersion(Long appId, Long appVersionId) {
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appId, appVersionId);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "App version does not exist");
        }
        return versionEntity;
    }

    private static String resolveFileName(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return "download.zip";
        }
        Path path = Path.of(storagePath);
        if (path.getFileName() == null) {
            return "download.zip";
        }
        return path.getFileName().toString();
    }

    public Path getPackagePath(CurrentUser currentUser, Long packageId) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ExportPackageEntity entity = exportPackageEntityMapper.selectOneById(packageId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出包不存在");
        }
        AiAppEntity appEntity = requireReadableApp(currentUser, entity.getAppId());
        Path path = Path.of(entity.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出文件不存在或已被清理");
        }
        return path;
    }

    private void writeZipPackage(Path packagePath, List<GeneratedFileEntity> generatedFiles) {
        try {
            Files.createDirectories(packagePath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(packagePath);
                 ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                for (GeneratedFileEntity generatedFile : generatedFiles) {
                    Path sourcePath = Paths.get(generatedFile.getStoragePath()).normalize();
                    if (!Files.exists(sourcePath)) {
                        throw new BusinessException(ErrorCode.NOT_FOUND, "Generated file does not exist on disk");
                    }
                    ZipEntry zipEntry = new ZipEntry(ZipEntryPathSupport.toSafeZipEntryName(generatedFile.getFilePath()));
                    zipOutputStream.putNextEntry(zipEntry);
                    try (InputStream inputStream = Files.newInputStream(sourcePath)) {
                        inputStream.transferTo(zipOutputStream);
                    }
                    zipOutputStream.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to create export package");
        }
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
