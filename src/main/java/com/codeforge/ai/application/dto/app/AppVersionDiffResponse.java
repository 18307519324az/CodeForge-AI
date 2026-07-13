package com.codeforge.ai.application.dto.app;

import java.util.List;

public record AppVersionDiffResponse(
        Long appId,
        Long fromVersionId,
        Integer fromVersionNo,
        String fromSnapshotHash,
        Long toVersionId,
        Integer toVersionNo,
        String toSnapshotHash,
        List<AppVersionDiffFileResponse> changedFiles
) {
}
