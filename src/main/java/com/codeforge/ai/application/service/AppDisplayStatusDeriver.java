package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AppDisplayStatus;
import com.codeforge.ai.domain.app.entity.AiAppEntity;

final class AppDisplayStatusDeriver {

    private AppDisplayStatusDeriver() {
    }

    static String derive(AiAppEntity app,
                         boolean hasRunningTask,
                         Integer generatedFileCount,
                         String latestTaskStatus,
                         String publicationStatus) {
        if ("ARCHIVED".equals(app.getStatus())) {
            return AppDisplayStatus.ARCHIVED.name();
        }
        if (hasRunningTask) {
            return AppDisplayStatus.GENERATING.name();
        }
        if (app.getCurrentVersionId() == null && "FAILED".equals(latestTaskStatus)) {
            return AppDisplayStatus.FAILED.name();
        }
        if ("PUBLISHED".equals(publicationStatus)) {
            return AppDisplayStatus.PUBLISHED.name();
        }
        if (app.getCurrentVersionId() != null
                && generatedFileCount != null
                && generatedFileCount > 0) {
            return AppDisplayStatus.READY.name();
        }
        return AppDisplayStatus.DRAFT.name();
    }
}
