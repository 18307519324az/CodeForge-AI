package com.codeforge.ai.shared.util;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GeneratedArtifactPathSupport {

    private static final Pattern WINDOWS_DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");
    private static final Pattern UNC_PREFIX = Pattern.compile("^//.*");

    private GeneratedArtifactPathSupport() {
    }

    public static Path resolveVersionRoot(Path storageRoot, Long appId, Long versionId) {
        Path root = storageRoot.toAbsolutePath().normalize();
        Path versionRoot = root.resolve("apps")
                .resolve(String.valueOf(appId))
                .resolve("versions")
                .resolve(String.valueOf(versionId))
                .toAbsolutePath()
                .normalize();
        if (!versionRoot.startsWith(root)) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }
        return versionRoot;
    }

    public static Path resolveStagingRoot(Path storageRoot, Long appId, String stagingToken) {
        Path root = storageRoot.toAbsolutePath().normalize();
        Path stagingRoot = root.resolve("apps")
                .resolve(String.valueOf(appId))
                .resolve("staging")
                .resolve(stagingToken)
                .toAbsolutePath()
                .normalize();
        if (!stagingRoot.startsWith(root)) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }
        return stagingRoot;
    }

    public static String normalizeRelativeFilePath(String filePath, String fileName) {
        String candidate = filePath == null || filePath.isBlank() ? fileName : filePath;
        if (candidate == null || candidate.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件路径不能为空");
        }
        if (candidate.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }

        String normalizedSeparators = candidate.replace('\\', '/').trim();
        if (normalizedSeparators.isBlank()) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }
        if (normalizedSeparators.startsWith("/")) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }
        if (WINDOWS_DRIVE_PREFIX.matcher(normalizedSeparators).matches()
                || UNC_PREFIX.matcher(normalizedSeparators).matches()) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }

        String[] segments = normalizedSeparators.split("/", -1);
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
            }
            if (segment.indexOf(':') >= 0) {
                throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
            }
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(segment);
        }
        return builder.toString();
    }

    public static Path resolveTargetPath(Path versionRoot, String relativeFilePath) {
        Path normalizedRoot = versionRoot.toAbsolutePath().normalize();
        String safeRelative = normalizeRelativeFilePath(relativeFilePath, relativeFilePath);
        Path target = normalizedRoot.resolve(safeRelative).toAbsolutePath().normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }
        return target;
    }

    public static void assertNoSymbolicLinksOnPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path cursor = absolute;
        while (cursor != null) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(cursor)) {
                throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
            }
            cursor = cursor.getParent();
        }
    }

    public static void assertNoSymbolicLinksUnder(Path root) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        assertNoSymbolicLinksOnPath(absoluteRoot);
        if (!Files.exists(absoluteRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(absoluteRoot)) {
            paths.forEach(GeneratedArtifactPathSupport::assertNoSymbolicLinksOnPath);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }
    }

    public static void assertParentChainWritable(Path targetPath) {
        Path parent = targetPath.getParent();
        if (parent == null) {
            return;
        }
        assertNoSymbolicLinksOnPath(parent);
    }

    public static String relativeStorageReference(Path versionRoot, Path absolutePath) {
        Path normalizedRoot = versionRoot.toAbsolutePath().normalize();
        Path normalizedTarget = absolutePath.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }
        return normalizedRoot.relativize(normalizedTarget).toString().replace('\\', '/');
    }

    public static Path resolveSourceStoragePath(Path sourceVersionRoot, String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本文件内容不存在");
        }
        Path candidate = Path.of(storagePath).toAbsolutePath().normalize();
        if (!candidate.startsWith(sourceVersionRoot.toAbsolutePath().normalize())) {
            throw new BusinessException(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        }
        assertNoSymbolicLinksOnPath(candidate);
        return candidate;
    }

    public static String detectFileType(String filePath) {
        int dot = filePath.lastIndexOf('.');
        if (dot < 0 || dot == filePath.length() - 1) {
            return "text/plain";
        }
        String extension = filePath.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            default -> "text/plain";
        };
    }
}
