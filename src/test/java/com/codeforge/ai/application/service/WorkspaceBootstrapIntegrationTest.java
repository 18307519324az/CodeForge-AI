package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.workspace.WorkspaceDetailResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceSummaryResponse;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.enums.UserStatus;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberRole;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberStatus;
import com.codeforge.ai.domain.workspace.enums.WorkspaceStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceMemberEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
class WorkspaceBootstrapIntegrationTest {

    private static final String USER_ACCOUNT = "bootstrap-user-8";

    @Autowired
    private WorkspaceApplicationService workspaceApplicationService;

    @Autowired
    private WorkspaceEntityMapper workspaceEntityMapper;

    @Autowired
    private WorkspaceMemberEntityMapper workspaceMemberEntityMapper;

    @Autowired
    private UserEntityMapper userEntityMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long userId;
    private long workspaceId = -1L;

    @BeforeEach
    void setUp() {
        userId = ensureUser();
    }

    @Test
    @Transactional
    void workspaceOwnerCanListOwnWorkspace() {
        seedDefaultWorkspaceWithMembership();

        PageRequest pageRequest = new PageRequest();
        pageRequest.setPageNo(1);
        pageRequest.setPageSize(20);
        PageResponse<WorkspaceSummaryResponse> page = workspaceApplicationService.listWorkspaces(
                currentUser(),
                pageRequest
        );

        assertThat(page.total()).isGreaterThanOrEqualTo(1);
        assertThat(page.records()).anyMatch(record -> workspaceId == record.id());
    }

    @Test
    @Transactional
    void defaultWorkspaceBootstrapReturnsExistingWorkspace() {
        seedDefaultWorkspaceWithMembership();

        WorkspaceDetailResponse response = workspaceApplicationService.getOrCreateDefaultWorkspace(currentUser());

        assertThat(response.id()).isEqualTo(workspaceId);
        assertThat(response.name()).isEqualTo(WorkspaceApplicationService.DEFAULT_WORKSPACE_NAME);
    }

    @Test
    @Transactional
    void defaultWorkspaceBootstrapRepairsMissingMembership() {
        seedDefaultWorkspaceWithoutMembership();

        WorkspaceDetailResponse response = workspaceApplicationService.getOrCreateDefaultWorkspace(currentUser());

        assertThat(response.id()).isEqualTo(workspaceId);
        WorkspaceMemberEntity membership = workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        assertThat(membership).isNotNull();
        assertThat(membership.getMemberRole()).isEqualTo(WorkspaceMemberRole.OWNER.name());
        assertThat(membership.getMemberStatus()).isEqualTo(WorkspaceMemberStatus.ACTIVE.name());
    }

    @Test
    @Transactional
    void defaultWorkspaceBootstrapIsIdempotent() {
        seedDefaultWorkspaceWithMembership();

        WorkspaceDetailResponse first = workspaceApplicationService.getOrCreateDefaultWorkspace(currentUser());
        WorkspaceDetailResponse second = workspaceApplicationService.getOrCreateDefaultWorkspace(currentUser());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(countDefaultWorkspaces()).isEqualTo(1);
        assertThat(countOwnerMembershipsForSeededWorkspace()).isEqualTo(1);
    }

    @Test
    void concurrentDefaultWorkspaceBootstrapShouldReturnSameWorkspace() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> removeDefaultWorkspaceIfPresent());

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Callable<WorkspaceDetailResponse> task = () -> workspaceApplicationService.getOrCreateDefaultWorkspace(currentUser());
            Future<WorkspaceDetailResponse> firstFuture = executorService.submit(task);
            Future<WorkspaceDetailResponse> secondFuture = executorService.submit(task);

            WorkspaceDetailResponse first = firstFuture.get();
            WorkspaceDetailResponse second = secondFuture.get();

            assertThat(first.id()).isNotNull();
            assertThat(second.id()).isEqualTo(first.id());
            assertThat(countDefaultWorkspaces()).isEqualTo(1);
            assertThat(countActiveMembershipsForDefaultWorkspace()).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    private CurrentUser currentUser() {
        return new CurrentUser(userId, USER_ACCOUNT, List.of("USER"));
    }

    private long ensureUser() {
        UserEntity existing = userEntityMapper.findByAccount(USER_ACCOUNT);
        if (existing != null) {
            return existing.getId();
        }
        UserEntity userEntity = UserEntity.builder()
                .account(USER_ACCOUNT)
                .passwordHash("hash")
                .displayName(USER_ACCOUNT)
                .email("bootstrap-user-8@example.com")
                .status(UserStatus.ACTIVE.name())
                .build();
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setUpdatedAt(LocalDateTime.now());
        userEntity.setIsDeleted(0);
        userEntityMapper.insertUser(userEntity);
        return userEntityMapper.findByAccount(USER_ACCOUNT).getId();
    }

    private void seedDefaultWorkspaceWithMembership() {
        seedDefaultWorkspace(false);
    }

    private void seedDefaultWorkspaceWithoutMembership() {
        seedDefaultWorkspace(true);
    }

    private void seedDefaultWorkspace(boolean skipMembership) {
        removeDefaultWorkspaceIfPresent();
        LocalDateTime now = LocalDateTime.now();
        WorkspaceEntity workspaceEntity = WorkspaceEntity.builder()
                .name(WorkspaceApplicationService.DEFAULT_WORKSPACE_NAME)
                .description(WorkspaceApplicationService.DEFAULT_WORKSPACE_DESCRIPTION)
                .ownerUserId(userId)
                .status(WorkspaceStatus.ACTIVE.name())
                .planCode("FREE")
                .build();
        workspaceEntity.setCreatedBy(userId);
        workspaceEntity.setUpdatedBy(userId);
        workspaceEntity.setCreatedAt(now);
        workspaceEntity.setUpdatedAt(now);
        workspaceEntity.setIsDeleted(0);
        workspaceEntityMapper.insertWorkspace(workspaceEntity);
        WorkspaceEntity persistedWorkspace = workspaceEntityMapper.findByOwnerUserIdAndName(
                userId, WorkspaceApplicationService.DEFAULT_WORKSPACE_NAME);
        assertThat(persistedWorkspace).isNotNull();
        workspaceId = persistedWorkspace.getId();

        if (!skipMembership) {
            WorkspaceMemberEntity membership = WorkspaceMemberEntity.builder()
                    .workspaceId(workspaceId)
                    .userId(userId)
                    .memberRole(WorkspaceMemberRole.OWNER.name())
                    .memberStatus(WorkspaceMemberStatus.ACTIVE.name())
                    .invitedBy(userId)
                    .joinedAt(now)
                    .build();
            membership.setCreatedBy(userId);
            membership.setUpdatedBy(userId);
            membership.setCreatedAt(now);
            membership.setUpdatedAt(now);
            membership.setIsDeleted(0);
            workspaceMemberEntityMapper.insertWorkspaceMember(membership);
        }
    }

    private void removeDefaultWorkspaceIfPresent() {
        workspaceMemberEntityMapper.findByUserId(userId).forEach(member ->
                workspaceMemberEntityMapper.deleteById(member.getId()));
        WorkspaceEntity existing = workspaceEntityMapper.findAnyByOwnerUserIdAndName(
                userId, WorkspaceApplicationService.DEFAULT_WORKSPACE_NAME);
        if (existing != null) {
            existing.setName(WorkspaceApplicationService.DEFAULT_WORKSPACE_NAME + "-purged-" + existing.getId());
            existing.setIsDeleted(1);
            workspaceEntityMapper.update(existing);
        }
    }

    private long countDefaultWorkspaces() {
        WorkspaceEntity workspace = workspaceEntityMapper.findByOwnerUserIdAndName(
                userId, WorkspaceApplicationService.DEFAULT_WORKSPACE_NAME);
        return workspace == null ? 0 : 1;
    }

    private long countOwnerMembershipsForSeededWorkspace() {
        return workspaceMemberEntityMapper.findByUserId(userId).stream()
                .filter(member -> member.getWorkspaceId() != null && workspaceId == member.getWorkspaceId())
                .filter(member -> WorkspaceMemberStatus.ACTIVE.name().equals(member.getMemberStatus()))
                .count();
    }

    private long countActiveMembershipsForDefaultWorkspace() {
        WorkspaceEntity workspace = workspaceEntityMapper.findByOwnerUserIdAndName(
                userId, WorkspaceApplicationService.DEFAULT_WORKSPACE_NAME);
        if (workspace == null) {
            return 0;
        }
        return workspaceMemberEntityMapper.findByUserId(userId).stream()
                .filter(member -> workspace.getId().equals(member.getWorkspaceId()))
                .filter(member -> WorkspaceMemberStatus.ACTIVE.name().equals(member.getMemberStatus()))
                .count();
    }
}
