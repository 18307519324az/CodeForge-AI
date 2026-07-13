package com.codeforge.ai.shared.util;

import java.nio.file.Path;

public final class DownloadResponseSupport {

    private DownloadResponseSupport() {
    }

    public static String safeAttachmentFilename(Path zipPath) {
        if (zipPath == null || zipPath.getFileName() == null) {
            return "download.zip";
        }
        return sanitizeFilename(zipPath.getFileName().toString());
    }

    public static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "download.zip";
        }
        String sanitized = rawFilename
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('"', '_')
                .replace('\\', '_')
                .replace('/', '_');
        if (sanitized.contains(":")) {
            sanitized = sanitized.substring(sanitized.lastIndexOf(':') + 1);
        }
        sanitized = sanitized.replaceAll("[\\x00-\\x1F\\x7F]", "_").trim();
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "download.zip";
        }
        return sanitized;
    }

    public static String contentDispositionAttachment(String filename) {
        return "attachment; filename=\"" + sanitizeFilename(filename) + "\"";
    }
}
