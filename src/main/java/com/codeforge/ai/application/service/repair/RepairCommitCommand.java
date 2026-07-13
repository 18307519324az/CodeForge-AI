package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.nio.file.Path;
import java.util.List;

public record RepairCommitCommand(
        CurrentUser currentUser,
        Long appId,
        AppVersionEntity sourceVersion,
        Path stagingRoot,
        Path storageRoot,
        List<PreparedRepairedFile> preparedFiles,
        boolean hasIndexHtml) {
}
