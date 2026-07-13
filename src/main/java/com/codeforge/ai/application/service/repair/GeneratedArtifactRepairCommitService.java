package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.application.dto.app.AppVersionRepairResponse;
import com.codeforge.ai.application.service.GeneratedArtifactRepairAuditService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.util.GeneratedArtifactPathSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class GeneratedArtifactRepairCommitService {

    public static final String MANUAL_REPAIR_SOURCE = "MANUAL_REPAIR";

    private final AiAppEntityMapper aiAppEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final GeneratedArtifactRepairAuditService repairAuditService;
    private final GeneratedArtifactRepairFilesystemSupport filesystemSupport;

    @Autowired(required = false)
    private GeneratedArtifactRepairTransactionProbe transactionProbe;

    @Transactional
    public AppVersionRepairResponse commit(RepairCommitCommand command) {
        assertTransactionActive();
        Path finalVersionRoot = null;
        Long repairedVersionId = null;
        try {
            AiAppEntity lockedApp = aiAppEntityMapper.selectForUpdateById(command.appId());
            recordSelectForUpdateProbe();
            if (lockedApp == null) {
                throw new BusinessException(ErrorCode.APP_NOT_FOUND);
            }

            int nextVersionNo = resolveNextVersionNoUnderLock(lockedApp.getId());
            AppVersionEntity repairedVersion = AppVersionEntity.builder()
                    .appId(lockedApp.getId())
                    .versionNo(nextVersionNo)
                    .versionSource(MANUAL_REPAIR_SOURCE)
                    .sourceTaskId(command.sourceVersion().getSourceTaskId())
                    .changeSummary("人工修复：转义规范化与品牌资源更新（来源 v"
                            + command.sourceVersion().getVersionNo() + "）")
                    .status("READY")
                    .build();
            repairedVersion.setCreatedBy(command.currentUser().requiredUserId());
            repairedVersion.setUpdatedBy(command.currentUser().requiredUserId());
            applyAuditFields(repairedVersion);
            appVersionEntityMapper.insertVersion(repairedVersion);
            repairedVersionId = repairedVersion.getId();

            Path storageRoot = command.storageRoot().toAbsolutePath().normalize();
            finalVersionRoot = GeneratedArtifactPathSupport.resolveVersionRoot(
                    storageRoot, lockedApp.getId(), repairedVersion.getId());
            registerFilesystemCompensation(
                    command.stagingRoot(),
                    finalVersionRoot,
                    storageRoot,
                    lockedApp.getId(),
                    repairedVersion.getId());

            filesystemSupport.moveStagingToFinal(command.stagingRoot(), finalVersionRoot);

            List<GeneratedFileEntity> repairedFiles = insertGeneratedFiles(
                    command, repairedVersion, finalVersionRoot);

            if (command.hasIndexHtml()) {
                appVersionEntityMapper.updatePreviewInfo(
                        repairedVersion.getId(),
                        "/api/v1/static-preview/" + repairedVersion.getId() + "/index.html",
                        "READY",
                        command.currentUser().requiredUserId());
            }

            aiAppEntityMapper.updateCurrentVersionId(
                    lockedApp.getId(), repairedVersion.getId(), command.currentUser().requiredUserId());
            repairAuditService.recordSuccessfulRepair(
                    command.currentUser().requiredUserId(),
                    lockedApp.getId(),
                    command.sourceVersion().getId(),
                    repairedVersion.getId(),
                    repairedVersion.getVersionNo());

            return new AppVersionRepairResponse(
                    lockedApp.getId(),
                    command.sourceVersion().getId(),
                    command.sourceVersion().getVersionNo(),
                    repairedVersion.getId(),
                    repairedVersion.getVersionNo(),
                    MANUAL_REPAIR_SOURCE,
                    repairedFiles.size(),
                    true);
        } catch (DuplicateKeyException exception) {
            cleanupFinalDirectory(finalVersionRoot, command.storageRoot(), command.appId(), repairedVersionId);
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "版本号冲突，请重试");
        } catch (RuntimeException | IOException exception) {
            cleanupFinalDirectory(finalVersionRoot, command.storageRoot(), command.appId(), repairedVersionId);
            throw translateCommitFailure(exception);
        }
    }

    private List<GeneratedFileEntity> insertGeneratedFiles(RepairCommitCommand command,
                                                           AppVersionEntity repairedVersion,
                                                           Path finalVersionRoot) {
        List<GeneratedFileEntity> repairedFiles = new ArrayList<>();
        for (PreparedRepairedFile preparedFile : command.preparedFiles()) {
            Path absolutePath = GeneratedArtifactPathSupport.resolveTargetPath(
                    finalVersionRoot, preparedFile.relativePath());
            GeneratedFileEntity repairedFile = GeneratedFileEntity.builder()
                    .appVersionId(repairedVersion.getId())
                    .filePath(preparedFile.relativePath())
                    .fileName(absolutePath.getFileName().toString())
                    .fileType(GeneratedArtifactPathSupport.detectFileType(preparedFile.relativePath()))
                    .fileContent(preparedFile.textContent())
                    .storagePath(absolutePath.toString())
                    .fileSize(preparedFile.byteSize())
                    .build();
            repairedFile.setCreatedBy(command.currentUser().requiredUserId());
            repairedFile.setUpdatedBy(command.currentUser().requiredUserId());
            applyAuditFields(repairedFile);
            generatedFileEntityMapper.insertFile(repairedFile);
            repairedFiles.add(repairedFile);
        }
        return repairedFiles;
    }

    private void assertTransactionActive() {
        boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        boolean synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive();
        if (transactionProbe != null) {
            transactionProbe.record(transactionActive, synchronizationActive, false);
        }
        if (!transactionActive) {
            throw new IllegalStateException("Repair commit must run inside an active Spring transaction");
        }
    }

    private void recordSelectForUpdateProbe() {
        if (transactionProbe != null) {
            transactionProbe.record(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isSynchronizationActive(),
                    true);
        }
    }

    private void registerFilesystemCompensation(Path stagingRoot,
                                                Path finalVersionRoot,
                                                Path storageRoot,
                                                Long appId,
                                                Long versionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Repair commit requires active transaction synchronization");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    filesystemSupport.deleteRepairDirectory(stagingRoot, storageRoot, appId, null);
                    return;
                }
                filesystemSupport.deleteRepairDirectory(stagingRoot, storageRoot, appId, null);
                filesystemSupport.deleteRepairDirectory(finalVersionRoot, storageRoot, appId, versionId);
            }
        });
    }

    private void cleanupFinalDirectory(Path finalVersionRoot,
                                       Path storageRoot,
                                       Long appId,
                                       Long versionId) {
        if (finalVersionRoot != null && versionId != null) {
            filesystemSupport.deleteRepairDirectory(finalVersionRoot, storageRoot, appId, versionId);
        }
    }

    private int resolveNextVersionNoUnderLock(Long appId) {
        Integer maxVersionNo = appVersionEntityMapper.findMaxVersionNo(appId);
        return (maxVersionNo == null ? 0 : maxVersionNo) + 1;
    }

    private RuntimeException translateCommitFailure(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException;
        }
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "修复产物提交失败");
    }

    private void applyAuditFields(AppVersionEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
    }

    private void applyAuditFields(GeneratedFileEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
    }
}
