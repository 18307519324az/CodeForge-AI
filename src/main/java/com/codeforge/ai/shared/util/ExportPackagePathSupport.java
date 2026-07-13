package com.codeforge.ai.shared.util;

import com.codeforge.ai.domain.app.enums.ExportPackageType;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ExportPackagePathSupport {

    private static final DateTimeFormatter FILE_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ExportPackagePathSupport() {
    }

    public static Path resolveExportRoot(String exportRoot) {
        return Path.of(exportRoot).toAbsolutePath().normalize();
    }

    public static Path resolveVersionRoot(Path exportRoot, Long appId, Integer versionNo) {
        Path root = exportRoot.toAbsolutePath().normalize();
        Path versionRoot = root.resolve("apps")
                .resolve(String.valueOf(appId))
                .resolve("versions")
                .resolve(String.valueOf(versionNo))
                .toAbsolutePath()
                .normalize();
        if (!versionRoot.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "导出路径越界");
        }
        return versionRoot;
    }

    public static Path resolvePackagePath(Path exportRoot,
                                          Long appId,
                                          Integer versionNo,
                                          ExportPackageType packageType,
                                          LocalDateTime createdAt) {
        Path versionRoot = resolveVersionRoot(exportRoot, appId, versionNo);
        String fileName = packageType.fileNamePrefix()
                + "_v" + versionNo
                + "_" + FILE_NAME_TIME_FORMATTER.format(createdAt)
                + ".zip";
        Path packagePath = versionRoot.resolve(fileName).toAbsolutePath().normalize();
        if (!packagePath.startsWith(versionRoot)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "导出路径越界");
        }
        return packagePath;
    }
}
