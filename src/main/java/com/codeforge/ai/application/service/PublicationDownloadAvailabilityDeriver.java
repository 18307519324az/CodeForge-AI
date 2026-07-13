package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.enums.PublicationDownloadAvailability;

public final class PublicationDownloadAvailabilityDeriver {

    private PublicationDownloadAvailabilityDeriver() {
    }

    public static PublicationDownloadAvailability derive(Boolean allowDownload, String exportStatus) {
        if (!Boolean.TRUE.equals(allowDownload)) {
            return PublicationDownloadAvailability.DISABLED;
        }
        if (exportStatus == null || exportStatus.isBlank()) {
            return PublicationDownloadAvailability.NOT_READY;
        }
        return switch (exportStatus.trim().toUpperCase()) {
            case "READY" -> PublicationDownloadAvailability.AVAILABLE;
            case "PROCESSING", "CREATED" -> PublicationDownloadAvailability.PROCESSING;
            case "FAILED" -> PublicationDownloadAvailability.FAILED;
            default -> PublicationDownloadAvailability.NOT_READY;
        };
    }
}
