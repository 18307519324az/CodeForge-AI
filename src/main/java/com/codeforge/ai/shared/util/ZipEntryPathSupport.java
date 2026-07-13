package com.codeforge.ai.shared.util;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;

public final class ZipEntryPathSupport {

    private ZipEntryPathSupport() {
    }

    public static String toSafeZipEntryName(String rawEntryPath) {
        if (rawEntryPath == null || rawEntryPath.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Invalid zip entry path");
        }
        String normalized = rawEntryPath.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Invalid zip entry path");
        }
        if (normalized.contains("..") || normalized.contains(":")) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Unsafe zip entry path");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Unsafe zip entry path");
            }
        }
        return normalized;
    }
}
