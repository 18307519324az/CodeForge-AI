package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberRole;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceMemberEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkspaceAccessService {

    private final WorkspaceEntityMapper workspaceEntityMapper;
    private final WorkspaceMemberEntityMapper workspaceMemberEntityMapper;

    public WorkspaceEntity requireWorkspace(Long workspaceId) {
        WorkspaceEntity workspaceEntity = workspaceEntityMapper.selectOneById(workspaceId);
        if (workspaceEntity == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }
        return workspaceEntity;
    }

    public WorkspaceEntity requireReadAccess(CurrentUser currentUser, Long workspaceId) {
        WorkspaceEntity workspaceEntity = requireWorkspace(workspaceId);
        if (currentUser.isPlatformAdmin()) {
            return workspaceEntity;
        }
        requireMembership(currentUser, workspaceId,
                WorkspaceMemberRole.OWNER, WorkspaceMemberRole.ADMIN,
                WorkspaceMemberRole.EDITOR, WorkspaceMemberRole.VIEWER);
        return workspaceEntity;
    }

    public WorkspaceEntity requireEditorAccess(CurrentUser currentUser, Long workspaceId) {
        WorkspaceEntity workspaceEntity = requireWorkspace(workspaceId);
        if (currentUser.isPlatformAdmin()) {
            return workspaceEntity;
        }
        requireMembership(currentUser, workspaceId,
                WorkspaceMemberRole.OWNER, WorkspaceMemberRole.ADMIN, WorkspaceMemberRole.EDITOR);
        return workspaceEntity;
    }

    public WorkspaceEntity requireAdminAccess(CurrentUser currentUser, Long workspaceId) {
        WorkspaceEntity workspaceEntity = requireWorkspace(workspaceId);
        if (currentUser.isPlatformAdmin()) {
            return workspaceEntity;
        }
        requireMembership(currentUser, workspaceId, WorkspaceMemberRole.OWNER, WorkspaceMemberRole.ADMIN);
        return workspaceEntity;
    }

    public List<Long> listReadableWorkspaceIds(CurrentUser currentUser) {
        if (currentUser.isPlatformAdmin()) {
            return workspaceEntityMapper.selectAll().stream()
                    .map(WorkspaceEntity::getId)
                    .toList();
        }
        return workspaceMemberEntityMapper.findByUserId(currentUser.requiredUserId()).stream()
                .filter(member -> WorkspaceMemberStatus.ACTIVE.name().equals(member.getMemberStatus()))
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .distinct()
                .toList();
    }

    private WorkspaceMemberEntity requireMembership(
            CurrentUser currentUser, Long workspaceId, WorkspaceMemberRole... allowedRoles) {
        WorkspaceMemberEntity membership = workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(
                workspaceId, currentUser.requiredUserId());
        if (membership == null || !WorkspaceMemberStatus.ACTIVE.name().equals(membership.getMemberStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        for (WorkspaceMemberRole allowedRole : allowedRoles) {
            if (allowedRole.name().equals(membership.getMemberRole())) {
                return membership;
            }
        }
        throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
    }
}
