package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.application.dto.app.AppVersionRepairResponse;
import com.codeforge.ai.application.service.GeneratedArtifactRepairApplicationService;
import com.codeforge.ai.application.service.GeneratedArtifactRepairAuditService;
import com.codeforge.ai.application.service.WorkspaceAccessService;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class RepairServiceTestFactory {

    private RepairServiceTestFactory() {
    }

    public static GeneratedArtifactRepairApplicationService create(
            AiAppEntityMapper aiAppEntityMapper,
            AppVersionEntityMapper appVersionEntityMapper,
            GeneratedFileEntityMapper generatedFileEntityMapper,
            WorkspaceAccessService workspaceAccessService,
            GeneratedArtifactRepairAuditService repairAuditService) {
        GeneratedArtifactRepairFilesystemSupport filesystemSupport = new GeneratedArtifactRepairFilesystemSupport();
        GeneratedArtifactRepairCommitService commitService = new GeneratedArtifactRepairCommitService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                repairAuditService,
                filesystemSupport);
        return new GeneratedArtifactRepairApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                workspaceAccessService,
                commitService,
                filesystemSupport);
    }

    public static GeneratedArtifactRepairApplicationService createWithMockedCommit(
            AiAppEntityMapper aiAppEntityMapper,
            AppVersionEntityMapper appVersionEntityMapper,
            GeneratedFileEntityMapper generatedFileEntityMapper,
            WorkspaceAccessService workspaceAccessService,
            GeneratedArtifactRepairCommitService commitService) {
        GeneratedArtifactRepairFilesystemSupport filesystemSupport = new GeneratedArtifactRepairFilesystemSupport();
        return new GeneratedArtifactRepairApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                workspaceAccessService,
                commitService,
                filesystemSupport);
    }

    public static GeneratedArtifactRepairCommitService mockSuccessfulCommit() {
        GeneratedArtifactRepairCommitService commitService = mock(GeneratedArtifactRepairCommitService.class);
        when(commitService.commit(any())).thenAnswer(invocation -> {
            RepairCommitCommand command = invocation.getArgument(0);
            return new AppVersionRepairResponse(
                    command.appId(),
                    command.sourceVersion().getId(),
                    command.sourceVersion().getVersionNo(),
                    command.sourceVersion().getId() + 1000L,
                    command.sourceVersion().getVersionNo() + 1,
                    GeneratedArtifactRepairCommitService.MANUAL_REPAIR_SOURCE,
                    command.preparedFiles().size(),
                    true);
        });
        return commitService;
    }

    public static void resetMocks(Object... mocks) {
        Mockito.reset(mocks);
    }
}
