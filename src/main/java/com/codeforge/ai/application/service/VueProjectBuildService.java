package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Build Vue projects to dist/ for preview.
 *
 * Directory layout:
 *   data/codeforge/build-workdir/{versionId}/   — npm install + npm run build working directory
 *   data/codeforge/previews/{versionId}/         — built dist/ output (stable path for serving)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VueProjectBuildService {

    private static final int PROCESS_TIMEOUT_MINUTES = 5;
    private static final int MAX_LOG_LENGTH = 10_000;

    private final GeneratedFileEntityMapper generatedFileEntityMapper;
    private final AppVersionEntityMapper appVersionEntityMapper;

    @Value("${codeforge.build.workdir:data/codeforge/build-workdir}")
    private String buildWorkdir;

    @Value("${codeforge.build.previewdir:data/codeforge/previews}")
    private String previewDir;

    @Value("${codeforge.build.node-path:node}")
    private String nodePath;

    @Value("${codeforge.build.npm-path:npm}")
    private String npmPath;

    private ExecutorService buildExecutor;

    @PostConstruct
    void init() {
        buildExecutor = Executors.newFixedThreadPool(2);
    }

    @PreDestroy
    void shutdown() {
        if (buildExecutor != null) {
            buildExecutor.shutdownNow();
        }
    }

    /**
     * Build a Vue project for the given version.
     * <p>
     * Steps:
     * 1. Write all generated_file records to build-workdir/{versionId}/
     * 2. Run npm install --ignore-scripts (5 min timeout)
     * 3. Verify package.json scripts are allowed (vite build only)
     * 4. Run npm run build (5 min timeout)
     * 5. On success: copy dist/ to previews/{versionId}/, update build_status/preview_status
     * 6. On failure: update build_status=FAILED, keep workdir for debugging
     *
     * @throws BusinessException if build fails
     */
    public void buildVersion(Long versionId, Long updatedBy) {
        // Mark as BUILDING
        appVersionEntityMapper.updateBuildStatus(versionId, "BUILDING", null, updatedBy);

        Path workDir = getWorkDir(versionId);
        Path previewDirPath = getPreviewDirPath(versionId);

        try {
            // Clean existing workdir if present
            deleteDirectory(workDir);
            Files.createDirectories(workDir);

            // 1. Write source files
            List<GeneratedFileEntity> files = generatedFileEntityMapper.findByAppVersionId(versionId);
            if (files.isEmpty()) {
                throw new BusinessException(ErrorCode.BUILD_FAILED, "No generated files found for version " + versionId);
            }
            writeSourceFiles(workDir, files);

            // 2. Check package.json exists
            Path packageJson = workDir.resolve("package.json");
            if (!Files.exists(packageJson)) {
                throw new BusinessException(ErrorCode.BUILD_FAILED, "package.json not found — cannot build Vue project");
            }

            // 3. npm install --ignore-scripts
            String installLog = runProcess(workDir, npmPath, "--ignore-scripts");
            log.info("npm install completed for version {}", versionId);

            // 4. Validate build script
            String buildScript = validateBuildScript(packageJson);

            // 5. npm run build (via the validated script)
            String buildLog = runProcess(workDir, npmPath, "run", buildScript);
            log.info("npm run build completed for version {}", versionId);

            // 6. Copy dist/ to previews/{versionId}/
            Path distDir = workDir.resolve("dist");
            if (!Files.isDirectory(distDir)) {
                throw new BusinessException(ErrorCode.BUILD_FAILED, "dist/ directory not found after build");
            }
            deleteDirectory(previewDirPath);
            Files.createDirectories(previewDirPath.getParent());
            copyDirectory(distDir, previewDirPath);

            // 7. Update success status
            appVersionEntityMapper.updateBuildStatus(versionId, "SUCCESS",
                    truncateLog(installLog + "\n" + buildLog), updatedBy);
            appVersionEntityMapper.updatePreviewInfo(versionId,
                    "/api/v1/static-preview/" + versionId + "/index.html",
                    "READY", updatedBy);

            log.info("Vue project build SUCCESS for version {}", versionId);

        } catch (BusinessException e) {
            appVersionEntityMapper.updateBuildStatus(versionId, "FAILED",
                    truncateLog(e.getMessage()), updatedBy);
            appVersionEntityMapper.updatePreviewInfo(versionId, null, "FAILED", updatedBy);
            throw e;
        } catch (Exception e) {
            String msg = "Build failed: " + e.getMessage();
            appVersionEntityMapper.updateBuildStatus(versionId, "FAILED",
                    truncateLog(msg), updatedBy);
            appVersionEntityMapper.updatePreviewInfo(versionId, null, "FAILED", updatedBy);
            throw new BusinessException(ErrorCode.BUILD_FAILED, msg);
        }
    }

    /**
     * Get the preview directory path for a version.
     */
    public Path getPreviewDirPath(Long versionId) {
        return Paths.get(previewDir).toAbsolutePath().normalize().resolve(String.valueOf(versionId));
    }

    /**
     * Serve a built file from the preview directory.
     */
    public Optional<byte[]> serveBuiltFile(Long versionId, String filePath) {
        Path previewDirPath = getPreviewDirPath(versionId);
        Path resolved = previewDirPath.resolve(filePath).normalize();
        // Prevent path traversal
        if (!resolved.startsWith(previewDirPath)) {
            return Optional.empty();
        }
        try {
            if (Files.exists(resolved) && Files.isRegularFile(resolved)) {
                return Optional.of(Files.readAllBytes(resolved));
            }
        } catch (IOException e) {
            log.warn("Failed to read built file {} for version {}", filePath, versionId, e);
        }
        return Optional.empty();
    }

    // ========== Private helpers ==========

    private Path getWorkDir(Long versionId) {
        return Paths.get(buildWorkdir).toAbsolutePath().normalize().resolve(String.valueOf(versionId));
    }

    private void writeSourceFiles(Path workDir, List<GeneratedFileEntity> files) throws IOException {
        for (GeneratedFileEntity file : files) {
            String relativePath = file.getFilePath() != null ? file.getFilePath() : file.getFileName();
            if (relativePath == null || relativePath.isBlank()) continue;
            relativePath = relativePath.replace("\\", "/");
            if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            Path target = workDir.resolve(relativePath).normalize();
            // Ensure it stays within workDir (path traversal check)
            if (!target.startsWith(workDir)) continue;
            Files.createDirectories(target.getParent());
            String content = file.getFileContent();
            if (content != null) {
                Files.writeString(target, content, StandardCharsets.UTF_8);
            } else {
                Files.write(target, new byte[0]);
            }
        }
    }

    private String runProcess(Path workDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read all output
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Wait with timeout
            boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.BUILD_FAILED,
                        "Process timed out after " + PROCESS_TIMEOUT_MINUTES + " minutes: " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new BusinessException(ErrorCode.BUILD_FAILED,
                        "Process exited with code " + exitCode + ". Output:\n" + truncateLog(output));
            }

            return output;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BUILD_FAILED,
                    "Failed to run process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUILD_FAILED, "Process interrupted");
        }
    }

    /**
     * Validate that package.json only contains allowed build scripts.
     */
    private String validateBuildScript(Path packageJson) throws IOException {
        String content = Files.readString(packageJson, StandardCharsets.UTF_8);
        // Simple check: look for "scripts" section, find "build"
        if (!content.contains("\"build\"")) {
            throw new BusinessException(ErrorCode.BUILD_FAILED,
                    "package.json has no 'build' script in scripts section");
        }
        // Only allow vite build
        if (!content.contains("vite build") && !content.contains("vue-cli-service build")) {
            throw new BusinessException(ErrorCode.BUILD_FAILED,
                    "Only 'vite build' or 'vue-cli-service build' scripts are allowed");
        }
        return "build";
    }

    private String truncateLog(String log) {
        if (log == null) return null;
        if (log.length() <= MAX_LOG_LENGTH) return log;
        return log.substring(0, MAX_LOG_LENGTH) + "\n... (truncated)";
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (var stream = Files.walk(path)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                log.warn("Failed to delete {}", p, e);
                            }
                        });
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
