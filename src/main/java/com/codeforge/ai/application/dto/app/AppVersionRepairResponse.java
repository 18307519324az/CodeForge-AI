package com.codeforge.ai.application.dto.app;

public record AppVersionRepairResponse(
        Long appId,
        Long sourceVersionId,
        Integer sourceVersionNo,
        Long repairedVersionId,
        Integer repairedVersionNo,
        String versionSource,
        int repairedFileCount,
        boolean currentVersionUpdated
) {
}
