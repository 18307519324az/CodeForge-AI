package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AppVersionRepairResponse;
import com.codeforge.ai.application.service.repair.GeneratedArtifactRepairCommitService;
import com.codeforge.ai.application.service.repair.GeneratedArtifactRepairFilesystemSupport;
import com.codeforge.ai.application.service.repair.PreparedRepairedFile;
import com.codeforge.ai.application.service.repair.RepairCommitCommand;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.parser.JsonEncodedFileContentDecoder;
import com.codeforge.ai.domain.generation.validation.GeneratedArtifactBudget;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.util.GeneratedArtifactPathSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeneratedArtifactRepairApplicationService {

    private static final String MASCOT_CLASSPATH = "brand/codeforge-mascot.png";
    private static final Path DEFAULT_STORAGE_ROOT = Path.of(".local-storage");

    private Path storageRoot = DEFAULT_STORAGE_ROOT;

    private final AiAppEntityMapper aiAppEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;
    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;
    private final GeneratedArtifactRepairCommitService repairCommitService;
    private final GeneratedArtifactRepairFilesystemSupport filesystemSupport;

    public AppVersionRepairResponse repairArtifactVersion(CurrentUser currentUser, Long appId, Long sourceVersionId) {
        AiAppEntity appEntity = requireEditableApp(currentUser, appId);
        AppVersionEntity sourceVersion = requireReadableVersion(currentUser, appId, sourceVersionId);
        List<GeneratedFileEntity> sourceFiles = generatedFileEntityMapper.findByAppVersionId(sourceVersion.getId());
        if (sourceFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "源版本没有可修复文件");
        }

        Path storageRoot = this.storageRoot.toAbsolutePath().normalize();
        Path sourceVersionRoot = GeneratedArtifactPathSupport.resolveVersionRoot(
                storageRoot, appEntity.getId(), sourceVersion.getId());
        GeneratedArtifactPathSupport.assertNoSymbolicLinksUnder(sourceVersionRoot);

        List<PreparedRepairedFile> preparedFiles = new ArrayList<>();
        for (GeneratedFileEntity sourceFile : sourceFiles) {
            preparedFiles.add(prepareRepairedFile(sourceVersionRoot, sourceFile));
        }

        boolean hasIndexHtml = preparedFiles.stream()
                .anyMatch(file -> "index.html".equalsIgnoreCase(file.relativePath()));
        appendMascotPreparedFile(preparedFiles);
        validateRepairBudget(preparedFiles);

        String stagingToken = UUID.randomUUID().toString().replace("-", "");
        Path stagingRoot = GeneratedArtifactPathSupport.resolveStagingRoot(storageRoot, appEntity.getId(), stagingToken);
        try {
            writePreparedFiles(stagingRoot, preparedFiles);
            return repairCommitService.commit(new RepairCommitCommand(
                    currentUser,
                    appEntity.getId(),
                    sourceVersion,
                    stagingRoot,
                    storageRoot,
                    preparedFiles,
                    hasIndexHtml));
        } catch (RuntimeException | IOException exception) {
            filesystemSupport.deleteRepairDirectory(stagingRoot, storageRoot, appEntity.getId(), null);
            throw translateRepairFailure(exception);
        }
    }

    private PreparedRepairedFile prepareRepairedFile(Path sourceVersionRoot, GeneratedFileEntity sourceFile) {
        String relativePath = GeneratedArtifactPathSupport.normalizeRelativeFilePath(
                sourceFile.getFilePath(), sourceFile.getFileName());
        if (relativePath.length() > GeneratedArtifactBudget.MAX_FILE_PATH_LENGTH) {
            throw new BusinessException(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
        }
        String fileName = Path.of(relativePath).getFileName().toString();
        if (fileName.length() > GeneratedArtifactBudget.MAX_FILE_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
        }

        String content = loadFileContent(sourceVersionRoot, sourceFile);
        String normalized;
        try {
            normalized = JsonEncodedFileContentDecoder.normalizeRepairFileContent(relativePath, content);
        } catch (AiGenerationFailureException exception) {
            throw new BusinessException(ErrorCode.AI_ARTIFACT_AMBIGUOUS_ENCODING, exception.getMessage());
        }
        normalized = BrandAssetReferenceRewriter.rewriteGeneratedContent(relativePath, normalized);
        long byteSize = normalized == null ? 0L : normalized.getBytes(StandardCharsets.UTF_8).length;
        if (byteSize > GeneratedArtifactBudget.MAX_SINGLE_TEXT_FILE_BYTES) {
            throw new BusinessException(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
        }
        return new PreparedRepairedFile(relativePath, normalized, byteSize, true);
    }

    private void appendMascotPreparedFile(List<PreparedRepairedFile> preparedFiles) {
        boolean alreadyPresent = preparedFiles.stream()
                .anyMatch(file -> BrandAssetReferenceRewriter.GENERATED_MASCOT_RELATIVE_PATH
                        .equalsIgnoreCase(file.relativePath()));
        if (alreadyPresent) {
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource(MASCOT_CLASSPATH);
            byte[] mascotBytes;
            try (InputStream inputStream = resource.getInputStream()) {
                mascotBytes = inputStream.readAllBytes();
            }
            if (mascotBytes.length > GeneratedArtifactBudget.MAX_SINGLE_BINARY_FILE_BYTES) {
                throw new BusinessException(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
            }
            preparedFiles.add(new PreparedRepairedFile(
                    BrandAssetReferenceRewriter.GENERATED_MASCOT_RELATIVE_PATH,
                    null,
                    mascotBytes.length,
                    false,
                    mascotBytes));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法读取品牌吉祥物资源");
        }
    }

    private void validateRepairBudget(List<PreparedRepairedFile> preparedFiles) {
        if (preparedFiles.size() > GeneratedArtifactBudget.MAX_FILE_COUNT) {
            throw new BusinessException(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
        }
        long totalBytes = preparedFiles.stream().mapToLong(PreparedRepairedFile::byteSize).sum();
        if (totalBytes > GeneratedArtifactBudget.MAX_TOTAL_TEXT_BYTES) {
            throw new BusinessException(ErrorCode.ARTIFACT_BUDGET_EXCEEDED);
        }
    }

    private void writePreparedFiles(Path stagingRoot, List<PreparedRepairedFile> preparedFiles) throws IOException {
        GeneratedArtifactPathSupport.assertNoSymbolicLinksOnPath(stagingRoot);
        Files.createDirectories(stagingRoot);
        for (PreparedRepairedFile preparedFile : preparedFiles) {
            Path targetPath = GeneratedArtifactPathSupport.resolveTargetPath(
                    stagingRoot, preparedFile.relativePath());
            GeneratedArtifactPathSupport.assertParentChainWritable(targetPath);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            if (preparedFile.textFile()) {
                Files.writeString(targetPath, preparedFile.textContent(), StandardCharsets.UTF_8);
            } else {
                Files.write(targetPath, preparedFile.binaryContent());
            }
        }
    }

    private String loadFileContent(Path sourceVersionRoot, GeneratedFileEntity fileEntity) {
        if (fileEntity.getFileContent() != null) {
            return fileEntity.getFileContent();
        }
        if (fileEntity.getStoragePath() == null || fileEntity.getStoragePath().isBlank()) {
            return "";
        }
        Path sourcePath = GeneratedArtifactPathSupport.resolveSourceStoragePath(
                sourceVersionRoot, fileEntity.getStoragePath());
        try {
            return Files.readString(sourcePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本文件内容不存在");
        }
    }

    private RuntimeException translateRepairFailure(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException;
        }
        if (exception instanceof AiGenerationFailureException generationFailureException) {
            return new BusinessException(
                    ErrorCode.AI_ARTIFACT_AMBIGUOUS_ENCODING,
                    generationFailureException.getMessage());
        }
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "修复产物失败");
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
        AiAppEntity appEntity = aiAppEntityMapper.selectOneById(appId);
        if (appEntity == null) {
            throw new BusinessException(ErrorCode.APP_NOT_FOUND);
        }
        workspaceAccessService.requireReadAccess(currentUser, appEntity.getWorkspaceId());
        AppVersionEntity versionEntity = appVersionEntityMapper.findByAppIdAndVersionId(appEntity.getId(), versionId);
        if (versionEntity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "App version does not exist");
        }
        return versionEntity;
    }
}
