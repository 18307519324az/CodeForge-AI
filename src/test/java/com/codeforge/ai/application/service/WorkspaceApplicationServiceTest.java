package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.workspace.WorkspaceCreateRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceDetailResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberAddRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberUpdateRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceUpdateRequest;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceMemberEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkspaceApplicationServiceTest {

    private WorkspaceEntityMapper workspaceEntityMapper;
    private WorkspaceMemberEntityMapper workspaceMemberEntityMapper;
    private UserEntityMapper userEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private WorkspaceApplicationService workspaceApplicationService;

    @BeforeEach
    void setUp() {
        workspaceEntityMapper = mock(WorkspaceEntityMapper.class);
        workspaceMemberEntityMapper = mock(WorkspaceMemberEntityMapper.class);
        userEntityMapper = mock(UserEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        workspaceApplicationService = new WorkspaceApplicationService(
                workspaceEntityMapper,
                workspaceMemberEntityMapper,
                userEntityMapper,
                workspaceAccessService
        );
    }

    @Test
    void shouldCreateWorkspaceAndOwnerMembership() {
        WorkspaceEntity persistedWorkspace = WorkspaceEntity.builder()
                .id(1001L)
                .name("Workspace A")
                .description("demo")
                .ownerUserId(2001L)
                .status("ACTIVE")
                .planCode("FREE")
                .build();
        persistedWorkspace.setCreatedAt(LocalDateTime.of(2026, 6, 22, 12, 0));
        persistedWorkspace.setUpdatedAt(persistedWorkspace.getCreatedAt());
        doAnswer(invocation -> {
            WorkspaceEntity entity = invocation.getArgument(0);
            return 1;
        }).when(workspaceEntityMapper).insertWorkspace(any(WorkspaceEntity.class));
        given(workspaceEntityMapper.findLatestByOwnerUserId(2001L)).willReturn(persistedWorkspace);
        doAnswer(invocation -> {
            WorkspaceMemberEntity entity = invocation.getArgument(0);
            entity.setId(5001L);
            return 1;
        }).when(workspaceMemberEntityMapper).insertWorkspaceMember(any(WorkspaceMemberEntity.class));

        WorkspaceCreateRequest request = new WorkspaceCreateRequest();
        request.setName("Workspace A");
        request.setDescription("demo");

        WorkspaceDetailResponse response = workspaceApplicationService.createWorkspace(
                new CurrentUser(2001L, "owner", List.of("USER")),
                request
        );

        ArgumentCaptor<WorkspaceEntity> workspaceCaptor = ArgumentCaptor.forClass(WorkspaceEntity.class);
        ArgumentCaptor<WorkspaceMemberEntity> memberCaptor = ArgumentCaptor.forClass(WorkspaceMemberEntity.class);
        verify(workspaceEntityMapper).insertWorkspace(workspaceCaptor.capture());
        verify(workspaceMemberEntityMapper).insertWorkspaceMember(memberCaptor.capture());

        WorkspaceEntity workspaceEntity = workspaceCaptor.getValue();
        WorkspaceMemberEntity memberEntity = memberCaptor.getValue();
        assertThat(workspaceEntity.getOwnerUserId()).isEqualTo(2001L);
        assertThat(memberEntity.getWorkspaceId()).isEqualTo(1001L);
        assertThat(memberEntity.getUserId()).isEqualTo(2001L);
        assertThat(memberEntity.getMemberRole()).isEqualTo("OWNER");
        assertThat(response.ownerUserId()).isEqualTo(2001L);
        assertThat(response.memberRole()).isEqualTo("OWNER");
    }

    @Test
    void shouldRejectWorkspaceDetailWhenUserIsNotMember() {
        given(workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(1001L, 2001L)).willReturn(null);

        assertThatThrownBy(() -> workspaceApplicationService.getWorkspace(
                new CurrentUser(2001L, "viewer", List.of("USER")),
                1001L
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_FORBIDDEN);
                });
    }

    @Test
    void shouldAddWorkspaceMember() {
        WorkspaceMemberAddRequest request = new WorkspaceMemberAddRequest();
        request.setUserId(3001L);
        request.setMemberRole("EDITOR");

        UserEntity userEntity = UserEntity.builder()
                .id(3001L)
                .account("editor")
                .displayName("Editor User")
                .build();
        given(userEntityMapper.selectOneById(3001L)).willReturn(userEntity);
        given(workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(1001L, 3001L)).willReturn(null);
        doAnswer(invocation -> {
            WorkspaceMemberEntity entity = invocation.getArgument(0);
            entity.setId(5001L);
            entity.setJoinedAt(LocalDateTime.of(2026, 6, 25, 10, 0));
            return 1;
        }).when(workspaceMemberEntityMapper).insertWorkspaceMember(any(WorkspaceMemberEntity.class));

        WorkspaceMemberResponse response = workspaceApplicationService.addMember(
                new CurrentUser(2001L, "owner", List.of("USER")),
                1001L,
                request
        );

        assertThat(response.memberId()).isEqualTo(5001L);
        assertThat(response.userId()).isEqualTo(3001L);
        assertThat(response.account()).isEqualTo("editor");
        assertThat(response.memberRole()).isEqualTo("EDITOR");
        verify(workspaceAccessService).requireAdminAccess(any(CurrentUser.class), eq(1001L));
    }

    @Test
    void shouldUpdateWorkspace() {
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest();
        request.setName("Workspace A Updated");
        request.setDescription("updated description");

        WorkspaceEntity existingWorkspace = WorkspaceEntity.builder()
                .id(1001L)
                .name("Workspace A")
                .description("demo")
                .ownerUserId(2001L)
                .status("ACTIVE")
                .planCode("FREE")
                .build();
        given(workspaceAccessService.requireAdminAccess(any(CurrentUser.class), eq(1001L))).willReturn(existingWorkspace);

        WorkspaceDetailResponse response = workspaceApplicationService.updateWorkspace(
                new CurrentUser(2001L, "owner", List.of("PLATFORM_ADMIN")),
                1001L,
                request
        );

        ArgumentCaptor<WorkspaceEntity> workspaceCaptor = ArgumentCaptor.forClass(WorkspaceEntity.class);
        verify(workspaceEntityMapper).update(workspaceCaptor.capture());
        WorkspaceEntity updatedWorkspace = workspaceCaptor.getValue();
        assertThat(updatedWorkspace.getId()).isEqualTo(1001L);
        assertThat(updatedWorkspace.getName()).isEqualTo("Workspace A Updated");
        assertThat(updatedWorkspace.getDescription()).isEqualTo("updated description");
        assertThat(updatedWorkspace.getUpdatedBy()).isEqualTo(2001L);
        assertThat(response.id()).isEqualTo(1001L);
        assertThat(response.name()).isEqualTo("Workspace A Updated");
        assertThat(response.description()).isEqualTo("updated description");
        assertThat(response.memberRole()).isEqualTo("OWNER");
    }

    @Test
    void shouldRejectDuplicateActiveWorkspaceMember() {
        WorkspaceMemberAddRequest request = new WorkspaceMemberAddRequest();
        request.setUserId(3001L);
        request.setMemberRole("VIEWER");
        given(userEntityMapper.selectOneById(3001L)).willReturn(UserEntity.builder().id(3001L).account("viewer").build());
        given(workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(1001L, 3001L)).willReturn(
                WorkspaceMemberEntity.builder()
                        .id(5001L)
                        .workspaceId(1001L)
                        .userId(3001L)
                        .memberRole("VIEWER")
                        .memberStatus("ACTIVE")
                        .build()
        );

        assertThatThrownBy(() -> workspaceApplicationService.addMember(
                new CurrentUser(2001L, "owner", List.of("USER")),
                1001L,
                request
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
                });
    }

    @Test
    void shouldUpdateWorkspaceMemberRole() {
        WorkspaceMemberUpdateRequest request = new WorkspaceMemberUpdateRequest();
        request.setMemberRole("ADMIN");
        WorkspaceMemberEntity existingMember = WorkspaceMemberEntity.builder()
                .id(5001L)
                .workspaceId(1001L)
                .userId(3001L)
                .memberRole("EDITOR")
                .memberStatus("ACTIVE")
                .joinedAt(LocalDateTime.of(2026, 6, 25, 10, 0))
                .build();
        UserEntity userEntity = UserEntity.builder()
                .id(3001L)
                .account("editor")
                .displayName("Editor User")
                .build();
        given(workspaceMemberEntityMapper.findByIdAndWorkspaceId(5001L, 1001L))
                .willReturn(existingMember)
                .willReturn(WorkspaceMemberEntity.builder()
                        .id(5001L)
                        .workspaceId(1001L)
                        .userId(3001L)
                        .memberRole("ADMIN")
                        .memberStatus("ACTIVE")
                        .joinedAt(existingMember.getJoinedAt())
                        .build());
        given(userEntityMapper.selectOneById(3001L)).willReturn(userEntity);

        WorkspaceMemberResponse response = workspaceApplicationService.updateMember(
                new CurrentUser(2001L, "owner", List.of("USER")),
                1001L,
                5001L,
                request
        );

        assertThat(response.memberRole()).isEqualTo("ADMIN");
        verify(workspaceMemberEntityMapper).updateMembership(eq(5001L), eq("ADMIN"), eq("ACTIVE"), isNull(),
                eq(existingMember.getJoinedAt()), eq(2001L));
    }

    @Test
    void shouldRejectRemovingOwnerMember() {
        given(workspaceMemberEntityMapper.findByIdAndWorkspaceId(5001L, 1001L)).willReturn(
                WorkspaceMemberEntity.builder()
                        .id(5001L)
                        .workspaceId(1001L)
                        .userId(2001L)
                        .memberRole("OWNER")
                        .memberStatus("ACTIVE")
                        .build()
        );

        assertThatThrownBy(() -> workspaceApplicationService.removeMember(
                new CurrentUser(2001L, "owner", List.of("USER")),
                1001L,
                5001L
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT);
                });
    }
}
