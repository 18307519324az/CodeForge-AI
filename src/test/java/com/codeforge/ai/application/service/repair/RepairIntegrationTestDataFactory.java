package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.application.service.GeneratedArtifactRepairAuditService;
import com.codeforge.ai.application.service.GeneratedArtifactRepairAuditService;
import com.codeforge.ai.application.service.repair.GeneratedArtifactRepairCommitService;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
import com.codeforge.ai.domain.auth.enums.UserStatus;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberRole;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberStatus;
import com.codeforge.ai.domain.workspace.enums.WorkspaceStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AuditLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceMemberEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.util.GeneratedArtifactPathSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RepairIntegrationTestDataFactory {

    public static final long REPAIR_USER_ID = 88101L;
    public static final long REPAIR_WORKSPACE_ID = 88102L;
    public static final long REPAIR_APP_ID = 88103L;
    public static final long REPAIR_SOURCE_VERSION_ID = 88104L;
    public static final long REPAIR_SOURCE_TASK_ID = 88105L;
    public static final int REPAIR_SOURCE_VERSION_NO = 3;

    public static final String ESCAPED_INDEX_HTML =
            "<!DOCTYPE html>\\n<html>\\n<head>\\n<title>Repair</title>\\n</head>\\n<body>\\n</body>\\n</html>";

    @Autowired
    private UserEntityMapper userEntityMapper;
    @Autowired
    private WorkspaceEntityMapper workspaceEntityMapper;
    @Autowired
    private WorkspaceMemberEntityMapper workspaceMemberEntityMapper;
    @Autowired
    private AiAppEntityMapper aiAppEntityMapper;
    @Autowired
    private AppVersionEntityMapper appVersionEntityMapper;
    @Autowired
    private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Autowired
    private GeneratedFileEntityMapper generatedFileEntityMapper;
    @Autowired
    private AuditLogEntityMapper auditLogEntityMapper;

    public RepairFixture seedRepairFixture(Path storageRoot) throws Exception {
        return seedRepairFixture(storageRoot, REPAIR_APP_ID, REPAIR_SOURCE_VERSION_ID, REPAIR_SOURCE_VERSION_NO);
    }

    public RepairFixture seedIsolatedRepairFixture(Path storageRoot) throws Exception {
        long appId = System.nanoTime();
        long sourceVersionId = appId + 1;
        ensureUser();
        ensureWorkspace();
        ensureMembership();
        long sourceTaskId = appId + 2;
        insertAppShell(appId);
        insertGenerationTask(appId, sourceTaskId);
        insertSourceVersion(appId, sourceVersionId, REPAIR_SOURCE_VERSION_NO, sourceTaskId);
        linkAppCurrentVersion(appId, sourceVersionId);
        insertSourceFile(sourceVersionId);
        ensureSourceFilesystem(storageRoot, appId, sourceVersionId);
        return new RepairFixture(
                REPAIR_USER_ID,
                REPAIR_WORKSPACE_ID,
                appId,
                sourceVersionId,
                REPAIR_SOURCE_VERSION_NO,
                storageRoot.toAbsolutePath().normalize());
    }

    public RepairFixture seedRepairFixture(Path storageRoot,
                                           long appId,
                                           long sourceVersionId,
                                           int sourceVersionNo) throws Exception {
        ensureUser();
        ensureWorkspace();
        ensureMembership();
        ensureAppShell(appId);
        ensureGenerationTask(appId, REPAIR_SOURCE_TASK_ID);
        ensureSourceVersion(appId, sourceVersionId, sourceVersionNo, REPAIR_SOURCE_TASK_ID);
        linkAppCurrentVersion(appId, sourceVersionId);
        ensureSourceFile(sourceVersionId);
        ensureSourceFilesystem(storageRoot, appId, sourceVersionId);

        return new RepairFixture(
                REPAIR_USER_ID,
                REPAIR_WORKSPACE_ID,
                appId,
                sourceVersionId,
                sourceVersionNo,
                storageRoot.toAbsolutePath().normalize());
    }

    private void ensureUser() {
        if (userEntityMapper.findById(REPAIR_USER_ID) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .id(REPAIR_USER_ID)
                .account("repair-owner")
                .passwordHash("hash")
                .displayName("Repair Owner")
                .email("repair-owner@test")
                .status(UserStatus.ACTIVE.name())
                .build();
        user.setCreatedBy(REPAIR_USER_ID);
        user.setUpdatedBy(REPAIR_USER_ID);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setIsDeleted(0);
        userEntityMapper.insert(user);
    }

    private void ensureWorkspace() {
        if (workspaceEntityMapper.selectOneById(REPAIR_WORKSPACE_ID) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        WorkspaceEntity workspace = WorkspaceEntity.builder()
                .id(REPAIR_WORKSPACE_ID)
                .name("Repair Workspace")
                .description("repair integration")
                .ownerUserId(REPAIR_USER_ID)
                .status(WorkspaceStatus.ACTIVE.name())
                .planCode("FREE")
                .build();
        workspace.setCreatedBy(REPAIR_USER_ID);
        workspace.setUpdatedBy(REPAIR_USER_ID);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspace.setIsDeleted(0);
        workspaceEntityMapper.insert(workspace);
    }

    private void ensureMembership() {
        if (workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(REPAIR_WORKSPACE_ID, REPAIR_USER_ID) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        WorkspaceMemberEntity member = WorkspaceMemberEntity.builder()
                .workspaceId(REPAIR_WORKSPACE_ID)
                .userId(REPAIR_USER_ID)
                .memberRole(WorkspaceMemberRole.OWNER.name())
                .memberStatus(WorkspaceMemberStatus.ACTIVE.name())
                .invitedBy(REPAIR_USER_ID)
                .joinedAt(now)
                .build();
        member.setCreatedBy(REPAIR_USER_ID);
        member.setUpdatedBy(REPAIR_USER_ID);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        member.setIsDeleted(0);
        workspaceMemberEntityMapper.insertWorkspaceMember(member);
    }

    private void insertAppShell(long appId) {
        LocalDateTime now = LocalDateTime.now();
        AiAppEntity app = AiAppEntity.builder()
                .id(appId)
                .workspaceId(REPAIR_WORKSPACE_ID)
                .name("Repair App " + appId)
                .description("repair integration app")
                .appType("WEB_APP")
                .status("DRAFT")
                .visibility("PRIVATE")
                .currentVersionId(null)
                .build();
        app.setCreatedBy(REPAIR_USER_ID);
        app.setUpdatedBy(REPAIR_USER_ID);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        app.setIsDeleted(0);
        aiAppEntityMapper.insertApp(app);
    }

    private void linkAppCurrentVersion(long appId, long currentVersionId) {
        aiAppEntityMapper.updateCurrentVersionId(appId, currentVersionId, REPAIR_USER_ID);
    }

    private void insertGenerationTask(long appId, long taskId) {
        LocalDateTime now = LocalDateTime.now();
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .id(taskId)
                .workspaceId(REPAIR_WORKSPACE_ID)
                .appId(appId)
                .taskType("RULE_GENERATE")
                .taskStatus(GenerationTaskStatus.SUCCESS.name())
                .requirement("repair integration task")
                .retryCount(0)
                .queuedAt(now)
                .startedAt(now)
                .finishedAt(now)
                .build();
        task.setCreatedBy(REPAIR_USER_ID);
        task.setUpdatedBy(REPAIR_USER_ID);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setIsDeleted(0);
        generationTaskEntityMapper.insert(task);
    }

    private void insertSourceVersion(long appId, long sourceVersionId, int sourceVersionNo, long sourceTaskId) {
        LocalDateTime now = LocalDateTime.now();
        AppVersionEntity sourceVersion = AppVersionEntity.builder()
                .id(sourceVersionId)
                .appId(appId)
                .versionNo(sourceVersionNo)
                .versionSource("AI_DIRECT")
                .sourceTaskId(sourceTaskId)
                .changeSummary("source")
                .status("READY")
                .build();
        sourceVersion.setCreatedBy(REPAIR_USER_ID);
        sourceVersion.setUpdatedBy(REPAIR_USER_ID);
        sourceVersion.setCreatedAt(now);
        sourceVersion.setUpdatedAt(now);
        sourceVersion.setIsDeleted(0);
        appVersionEntityMapper.insert(sourceVersion);
    }

    private void insertSourceFile(long sourceVersionId) {
        LocalDateTime now = LocalDateTime.now();
        GeneratedFileEntity indexFile = GeneratedFileEntity.builder()
                .appVersionId(sourceVersionId)
                .filePath("index.html")
                .fileName("index.html")
                .fileType("text/html")
                .fileContent(ESCAPED_INDEX_HTML)
                .fileSize((long) ESCAPED_INDEX_HTML.getBytes(StandardCharsets.UTF_8).length)
                .build();
        indexFile.setCreatedBy(REPAIR_USER_ID);
        indexFile.setUpdatedBy(REPAIR_USER_ID);
        indexFile.setCreatedAt(now);
        indexFile.setUpdatedAt(now);
        indexFile.setIsDeleted(0);
        generatedFileEntityMapper.insertFile(indexFile);
    }

    private void ensureAppShell(long appId) {
        if (aiAppEntityMapper.selectOneById(appId) != null) {
            return;
        }
        insertAppShell(appId);
    }

    private void ensureGenerationTask(long appId, long taskId) {
        if (generationTaskEntityMapper.selectOneById(taskId) != null) {
            return;
        }
        insertGenerationTask(appId, taskId);
    }

    private void ensureSourceVersion(long appId, long sourceVersionId, int sourceVersionNo, long sourceTaskId) {
        if (appVersionEntityMapper.findById(sourceVersionId) != null) {
            return;
        }
        insertSourceVersion(appId, sourceVersionId, sourceVersionNo, sourceTaskId);
    }

    private void ensureSourceFile(long sourceVersionId) {
        if (!generatedFileEntityMapper.findByAppVersionId(sourceVersionId).isEmpty()) {
            return;
        }
        insertSourceFile(sourceVersionId);
    }

    private void ensureSourceFilesystem(Path storageRoot, long appId, long sourceVersionId) throws Exception {
        Path sourceVersionRoot = GeneratedArtifactPathSupport.resolveVersionRoot(
                storageRoot.toAbsolutePath().normalize(), appId, sourceVersionId);
        Files.createDirectories(sourceVersionRoot);
        Path indexPath = sourceVersionRoot.resolve("index.html");
        if (!Files.exists(indexPath)) {
            Files.writeString(indexPath, ESCAPED_INDEX_HTML, StandardCharsets.UTF_8);
        }
    }

    public CurrentUser ownerUser() {
        return new CurrentUser(REPAIR_USER_ID, "repair-owner", List.of("USER"));
    }

    public long countVersionsForApp(long appId) {
        return appVersionEntityMapper.findByAppId(appId).stream()
                .filter(version -> GeneratedArtifactRepairCommitService.MANUAL_REPAIR_SOURCE
                        .equals(version.getVersionSource()))
                .count();
    }

    public long countGeneratedFilesForVersion(long versionId) {
        return generatedFileEntityMapper.findByAppVersionId(versionId).size();
    }

    public long countRepairAudits() {
        return auditLogEntityMapper.findLatestLogs().stream()
                .filter(log -> GeneratedArtifactRepairAuditService.ACTION_ARTIFACT_REPAIR.equals(log.getActionCode()))
                .count();
    }

    public AiAppEntity loadApp(long appId) {
        return aiAppEntityMapper.selectOneById(appId);
    }

    public AppVersionEntity loadVersion(long versionId) {
        return appVersionEntityMapper.findById(versionId);
    }

    public Path sourceVersionRoot(RepairFixture fixture) {
        return GeneratedArtifactPathSupport.resolveVersionRoot(
                fixture.storageRoot(), fixture.appId(), fixture.sourceVersionId());
    }

    public record RepairFixture(
            long ownerUserId,
            long workspaceId,
            long appId,
            long sourceVersionId,
            int sourceVersionNo,
            Path storageRoot) {
    }
}
