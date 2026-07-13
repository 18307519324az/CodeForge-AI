package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.util.GeneratedArtifactPathSupport;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeneratedArtifactRepairFilesystemSupport {

    public void moveStagingToFinal(Path stagingRoot, Path finalVersionRoot) throws IOException {
        if (Files.exists(finalVersionRoot)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本目录已存在");
        }
        if (finalVersionRoot.getParent() != null) {
            Files.createDirectories(finalVersionRoot.getParent());
        }
        try {
            Files.move(stagingRoot, finalVersionRoot, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(stagingRoot, finalVersionRoot, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void deleteRepairDirectory(Path directory, Path storageRoot, Long appId, Long versionId) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        Path normalizedStorage = storageRoot.toAbsolutePath().normalize();
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (!normalizedDirectory.startsWith(normalizedStorage)) {
            log.warn("artifact repair cleanup skipped outside storage root appId={} versionId={}",
                    appId, versionId);
            return;
        }
        if (versionId != null) {
            Path expectedVersionRoot = GeneratedArtifactPathSupport.resolveVersionRoot(
                    normalizedStorage, appId, versionId);
            if (!normalizedDirectory.equals(expectedVersionRoot)) {
                log.warn("artifact repair cleanup skipped unexpected version root appId={} versionId={}",
                        appId, versionId);
                return;
            }
        } else {
            Path appRoot = normalizedStorage.resolve("apps").resolve(String.valueOf(appId));
            Path stagingRoot = appRoot.resolve("staging");
            if (!normalizedDirectory.startsWith(stagingRoot)) {
                log.warn("artifact repair cleanup skipped unexpected staging root appId={}", appId);
                return;
            }
        }
        deleteDirectoryQuietly(normalizedDirectory);
    }

    public void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("artifact repair cleanup failed appDirToken={} error={}",
                            directory.getFileName(), exception.getClass().getSimpleName());
                }
            });
        } catch (IOException exception) {
            log.warn("artifact repair cleanup walk failed appDirToken={} error={}",
                    directory.getFileName(), exception.getClass().getSimpleName());
        }
    }
}
