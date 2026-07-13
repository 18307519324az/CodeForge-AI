package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.workspace.WorkspaceCreateRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceDetailResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberAddRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberUpdateRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceSummaryResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceUpdateRequest;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberRole;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberStatus;
import com.codeforge.ai.domain.workspace.enums.WorkspaceStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceMemberEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.PageResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceApplicationService {

    public static final String DEFAULT_WORKSPACE_NAME = "默认工作空间";
    public static final String DEFAULT_WORKSPACE_DESCRIPTION = "用于管理 CodeForge AI 应用";
    private static final String DEFAULT_WORKSPACE_OWNER_NAME_INDEX = "uk_workspace_owner_name";
    private static final String WORKSPACE_MEMBER_UNIQUE_INDEX = "uk_workspace_member";

    private static final String WORKSPACE_MEMBER_NOT_FOUND_MESSAGE = "工作空间成员不存在";
    private static final String OWNER_ROLE_LOCKED_MESSAGE = "OWNER 成员不允许变更或移除";

    private final WorkspaceEntityMapper workspaceEntityMapper;
    private final WorkspaceMemberEntityMapper workspaceMemberEntityMapper;
    private final UserEntityMapper userEntityMapper;
    private final WorkspaceAccessService workspaceAccessService;

    @Transactional
    public WorkspaceDetailResponse createWorkspace(CurrentUser currentUser, WorkspaceCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        WorkspaceEntity workspaceEntity = WorkspaceEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerUserId(currentUser.requiredUserId())
                .status(WorkspaceStatus.ACTIVE.name())
                .planCode("FREE")
                .build();
        workspaceEntity.setCreatedBy(currentUser.requiredUserId());
        workspaceEntity.setUpdatedBy(currentUser.requiredUserId());
        workspaceEntity.setCreatedAt(now);
        workspaceEntity.setUpdatedAt(now);
        workspaceEntity.setIsDeleted(0);
        workspaceEntityMapper.insertWorkspace(workspaceEntity);
        WorkspaceEntity persistedWorkspace = workspaceEntityMapper.findLatestByOwnerUserId(currentUser.requiredUserId());
        if (persistedWorkspace == null || persistedWorkspace.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "工作空间创建后未返回主键");
        }
        workspaceEntity.setId(persistedWorkspace.getId());
        workspaceEntity.setCreatedAt(persistedWorkspace.getCreatedAt());
        workspaceEntity.setUpdatedAt(persistedWorkspace.getUpdatedAt());

        WorkspaceMemberEntity ownerMember = WorkspaceMemberEntity.builder()
                .workspaceId(workspaceEntity.getId())
                .userId(currentUser.requiredUserId())
                .memberRole(WorkspaceMemberRole.OWNER.name())
                .memberStatus(WorkspaceMemberStatus.ACTIVE.name())
                .invitedBy(currentUser.requiredUserId())
                .joinedAt(now)
                .build();
        ownerMember.setCreatedBy(currentUser.requiredUserId());
        ownerMember.setUpdatedBy(currentUser.requiredUserId());
        ownerMember.setCreatedAt(now);
        ownerMember.setUpdatedAt(now);
        ownerMember.setIsDeleted(0);
        workspaceMemberEntityMapper.insertWorkspaceMember(ownerMember);
        return toDetailResponse(workspaceEntity, ownerMember.getMemberRole());
    }

    public PageResponse<WorkspaceSummaryResponse> listWorkspaces(CurrentUser currentUser, PageRequest pageRequest) {
        validatePageRequest(pageRequest);
        List<WorkspaceMemberEntity> memberships = workspaceMemberEntityMapper.findByUserId(currentUser.requiredUserId()).stream()
                .filter(member -> WorkspaceMemberStatus.ACTIVE.name().equals(member.getMemberStatus()))
                .toList();
        List<Long> workspaceIds = workspaceAccessService.listReadableWorkspaceIds(currentUser);
        if (workspaceIds.isEmpty()) {
            return PageResponse.<WorkspaceSummaryResponse>builder()
                    .records(List.of())
                    .pageNo(pageRequest.getPageNo())
                    .pageSize(pageRequest.getPageSize())
                    .total(0)
                    .build();
        }
        Map<Long, WorkspaceMemberEntity> membershipMap = memberships.stream()
                .collect(Collectors.toMap(WorkspaceMemberEntity::getWorkspaceId, Function.identity(), (left, right) -> left));
        List<WorkspaceSummaryResponse> allRecords = workspaceEntityMapper.selectListByIds(workspaceIds).stream()
                .sorted(Comparator.comparing(WorkspaceEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(workspace -> new WorkspaceSummaryResponse(
                        workspace.getId(),
                        workspace.getName(),
                        workspace.getDescription(),
                        workspace.getOwnerUserId(),
                        workspace.getStatus(),
                        workspace.getPlanCode(),
                        currentUser.isPlatformAdmin()
                                ? WorkspaceMemberRole.OWNER.name()
                                : membershipMap.get(workspace.getId()).getMemberRole()
                ))
                .toList();
        int fromIndex = (int) ((pageRequest.getPageNo() - 1) * pageRequest.getPageSize());
        if (fromIndex >= allRecords.size()) {
            return PageResponse.<WorkspaceSummaryResponse>builder()
                    .records(List.of())
                    .pageNo(pageRequest.getPageNo())
                    .pageSize(pageRequest.getPageSize())
                    .total(allRecords.size())
                    .build();
        }
        int toIndex = Math.min(allRecords.size(), fromIndex + (int) pageRequest.getPageSize());
        return PageResponse.<WorkspaceSummaryResponse>builder()
                .records(allRecords.subList(fromIndex, toIndex))
                .pageNo(pageRequest.getPageNo())
                .pageSize(pageRequest.getPageSize())
                .total(allRecords.size())
                .build();
    }

    public WorkspaceDetailResponse getWorkspace(CurrentUser currentUser, Long workspaceId) {
        WorkspaceEntity workspaceEntity = workspaceAccessService.requireReadAccess(currentUser, workspaceId);
        String memberRole = currentUser.isPlatformAdmin()
                ? WorkspaceMemberRole.OWNER.name()
                : requireMembership(workspaceId, currentUser.requiredUserId()).getMemberRole();
        return toDetailResponse(workspaceEntity, memberRole);
    }

    @Transactional
    public WorkspaceDetailResponse updateWorkspace(CurrentUser currentUser, Long workspaceId, WorkspaceUpdateRequest request) {
        WorkspaceEntity workspaceEntity = workspaceAccessService.requireAdminAccess(currentUser, workspaceId);
        workspaceEntity.setName(request.getName());
        workspaceEntity.setDescription(request.getDescription());
        workspaceEntity.setUpdatedBy(currentUser.requiredUserId());
        workspaceEntityMapper.update(workspaceEntity);
        String memberRole = currentUser.isPlatformAdmin()
                ? WorkspaceMemberRole.OWNER.name()
                : requireMembership(workspaceId, currentUser.requiredUserId()).getMemberRole();
        return toDetailResponse(workspaceEntity, memberRole);
    }

    public List<WorkspaceMemberResponse> listMembers(CurrentUser currentUser, Long workspaceId) {
        workspaceAccessService.requireReadAccess(currentUser, workspaceId);
        List<WorkspaceMemberEntity> members = workspaceMemberEntityMapper.findByWorkspaceId(workspaceId).stream()
                .filter(member -> WorkspaceMemberStatus.ACTIVE.name().equals(member.getMemberStatus()))
                .toList();
        return buildMemberResponses(members);
    }

    @Transactional
    public WorkspaceMemberResponse addMember(CurrentUser currentUser, Long workspaceId, WorkspaceMemberAddRequest request) {
        workspaceAccessService.requireAdminAccess(currentUser, workspaceId);
        WorkspaceMemberRole memberRole = parseMemberRole(request.getMemberRole());
        UserEntity userEntity = requireUser(request.getUserId());
        WorkspaceMemberEntity existingMembership = workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(workspaceId, request.getUserId());
        if (existingMembership != null) {
            if (WorkspaceMemberStatus.ACTIVE.name().equals(existingMembership.getMemberStatus())) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "成员已存在");
            }
            LocalDateTime now = LocalDateTime.now();
            workspaceMemberEntityMapper.updateMembership(
                    existingMembership.getId(),
                    memberRole.name(),
                    WorkspaceMemberStatus.ACTIVE.name(),
                    currentUser.requiredUserId(),
                    now,
                    currentUser.requiredUserId()
            );
            WorkspaceMemberEntity refreshedMembership = workspaceMemberEntityMapper.findByIdAndWorkspaceId(
                    existingMembership.getId(), workspaceId);
            return toMemberResponse(refreshedMembership, userEntity);
        }

        WorkspaceMemberEntity memberEntity = WorkspaceMemberEntity.builder()
                .workspaceId(workspaceId)
                .userId(request.getUserId())
                .memberRole(memberRole.name())
                .memberStatus(WorkspaceMemberStatus.ACTIVE.name())
                .invitedBy(currentUser.requiredUserId())
                .joinedAt(LocalDateTime.now())
                .build();
        memberEntity.setCreatedBy(currentUser.requiredUserId());
        memberEntity.setUpdatedBy(currentUser.requiredUserId());
        memberEntity.setCreatedAt(LocalDateTime.now());
        memberEntity.setUpdatedAt(LocalDateTime.now());
        memberEntity.setIsDeleted(0);
        workspaceMemberEntityMapper.insertWorkspaceMember(memberEntity);
        return toMemberResponse(memberEntity, userEntity);
    }

    @Transactional
    public WorkspaceMemberResponse updateMember(
            CurrentUser currentUser, Long workspaceId, Long memberId, WorkspaceMemberUpdateRequest request) {
        workspaceAccessService.requireAdminAccess(currentUser, workspaceId);
        WorkspaceMemberRole memberRole = parseMemberRole(request.getMemberRole());
        WorkspaceMemberEntity memberEntity = requireWorkspaceMember(workspaceId, memberId);
        if (WorkspaceMemberRole.OWNER.name().equals(memberEntity.getMemberRole())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, OWNER_ROLE_LOCKED_MESSAGE);
        }
        workspaceMemberEntityMapper.updateMembership(
                memberEntity.getId(),
                memberRole.name(),
                memberEntity.getMemberStatus(),
                memberEntity.getInvitedBy(),
                memberEntity.getJoinedAt(),
                currentUser.requiredUserId()
        );
        WorkspaceMemberEntity refreshedMember = requireWorkspaceMember(workspaceId, memberId);
        UserEntity userEntity = requireUser(refreshedMember.getUserId());
        return toMemberResponse(refreshedMember, userEntity);
    }

    @Transactional
    public void removeMember(CurrentUser currentUser, Long workspaceId, Long memberId) {
        workspaceAccessService.requireAdminAccess(currentUser, workspaceId);
        WorkspaceMemberEntity memberEntity = requireWorkspaceMember(workspaceId, memberId);
        if (WorkspaceMemberRole.OWNER.name().equals(memberEntity.getMemberRole())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, OWNER_ROLE_LOCKED_MESSAGE);
        }
        workspaceMemberEntityMapper.updateMembership(
                memberEntity.getId(),
                memberEntity.getMemberRole(),
                WorkspaceMemberStatus.REMOVED.name(),
                memberEntity.getInvitedBy(),
                memberEntity.getJoinedAt(),
                currentUser.requiredUserId()
        );
    }

    private WorkspaceEntity requireWorkspace(Long workspaceId) {
        WorkspaceEntity workspaceEntity = workspaceEntityMapper.selectOneById(workspaceId);
        if (workspaceEntity == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }
        return workspaceEntity;
    }

    private WorkspaceMemberEntity requireMembership(Long workspaceId, Long userId) {
        WorkspaceMemberEntity membership = workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (membership == null || !WorkspaceMemberStatus.ACTIVE.name().equals(membership.getMemberStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return membership;
    }

    private WorkspaceMemberEntity requireWorkspaceMember(Long workspaceId, Long memberId) {
        WorkspaceMemberEntity memberEntity = workspaceMemberEntityMapper.findByIdAndWorkspaceId(memberId, workspaceId);
        if (memberEntity == null || WorkspaceMemberStatus.REMOVED.name().equals(memberEntity.getMemberStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, WORKSPACE_MEMBER_NOT_FOUND_MESSAGE);
        }
        return memberEntity;
    }

    private UserEntity requireUser(Long userId) {
        UserEntity userEntity = userEntityMapper.selectOneById(userId);
        if (userEntity == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return userEntity;
    }

    private WorkspaceMemberRole parseMemberRole(String memberRole) {
        try {
            return WorkspaceMemberRole.valueOf(memberRole);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "memberRole 非法");
        }
    }

    private List<WorkspaceMemberResponse> buildMemberResponses(List<WorkspaceMemberEntity> members) {
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, UserEntity> userMap = userEntityMapper.findByIds(members.stream()
                        .map(WorkspaceMemberEntity::getUserId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return members.stream()
                .map(member -> {
                    UserEntity userEntity = userMap.get(member.getUserId());
                    if (userEntity == null) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                    }
                    return toMemberResponse(member, userEntity);
                })
                .toList();
    }

    private WorkspaceMemberResponse toMemberResponse(WorkspaceMemberEntity memberEntity, UserEntity userEntity) {
        return new WorkspaceMemberResponse(
                memberEntity.getId(),
                memberEntity.getUserId(),
                userEntity.getAccount(),
                userEntity.getDisplayName(),
                memberEntity.getMemberRole(),
                memberEntity.getMemberStatus(),
                memberEntity.getJoinedAt()
        );
    }

    private WorkspaceDetailResponse toDetailResponse(WorkspaceEntity workspaceEntity, String memberRole) {
        return new WorkspaceDetailResponse(
                workspaceEntity.getId(),
                workspaceEntity.getName(),
                workspaceEntity.getDescription(),
                workspaceEntity.getOwnerUserId(),
                workspaceEntity.getStatus(),
                workspaceEntity.getPlanCode(),
                memberRole
        );
    }

    @Transactional
    public WorkspaceDetailResponse getOrCreateDefaultWorkspace(CurrentUser currentUser) {
        Long userId = currentUser.requiredUserId();
        WorkspaceDetailResponse fromMembership = findDefaultWorkspaceViaActiveMembership(userId);
        if (fromMembership != null) {
            return fromMembership;
        }

        WorkspaceEntity canonicalWorkspace = workspaceEntityMapper.findByOwnerUserIdAndName(
                userId, DEFAULT_WORKSPACE_NAME);
        if (canonicalWorkspace != null) {
            WorkspaceMemberEntity ownerMembership = ensureOwnerMembership(canonicalWorkspace.getId(), userId);
            return toDetailResponse(canonicalWorkspace, ownerMembership.getMemberRole());
        }

        try {
            return createDefaultWorkspace(currentUser);
        } catch (DuplicateKeyException exception) {
            if (isDefaultWorkspaceOwnerNameConflict(exception)) {
                WorkspaceEntity existing = workspaceEntityMapper.findByOwnerUserIdAndName(
                        userId, DEFAULT_WORKSPACE_NAME);
                if (existing == null) {
                    throw exception;
                }
                WorkspaceMemberEntity ownerMembership = ensureOwnerMembership(existing.getId(), userId);
                return toDetailResponse(existing, ownerMembership.getMemberRole());
            }
            throw exception;
        }
    }

    private WorkspaceDetailResponse findDefaultWorkspaceViaActiveMembership(Long userId) {
        for (WorkspaceMemberEntity membership : workspaceMemberEntityMapper.findByUserId(userId)) {
            if (!WorkspaceMemberStatus.ACTIVE.name().equals(membership.getMemberStatus())) {
                continue;
            }
            WorkspaceEntity workspaceEntity = workspaceEntityMapper.selectOneById(membership.getWorkspaceId());
            if (workspaceEntity == null || Integer.valueOf(1).equals(workspaceEntity.getIsDeleted())) {
                continue;
            }
            if (userId.equals(workspaceEntity.getOwnerUserId())
                    && DEFAULT_WORKSPACE_NAME.equals(workspaceEntity.getName())) {
                return toDetailResponse(workspaceEntity, membership.getMemberRole());
            }
        }
        return null;
    }

    private WorkspaceDetailResponse createDefaultWorkspace(CurrentUser currentUser) {
        WorkspaceCreateRequest defaultRequest = new WorkspaceCreateRequest();
        defaultRequest.setName(DEFAULT_WORKSPACE_NAME);
        defaultRequest.setDescription(DEFAULT_WORKSPACE_DESCRIPTION);
        return createWorkspace(currentUser, defaultRequest);
    }

    private WorkspaceMemberEntity ensureOwnerMembership(Long workspaceId, Long userId) {
        WorkspaceMemberEntity activeMembership = workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (activeMembership != null) {
            if (WorkspaceMemberStatus.ACTIVE.name().equals(activeMembership.getMemberStatus())
                    && WorkspaceMemberRole.OWNER.name().equals(activeMembership.getMemberRole())) {
                return activeMembership;
            }
            LocalDateTime now = LocalDateTime.now();
            workspaceMemberEntityMapper.updateMembership(
                    activeMembership.getId(),
                    WorkspaceMemberRole.OWNER.name(),
                    WorkspaceMemberStatus.ACTIVE.name(),
                    userId,
                    activeMembership.getJoinedAt() != null ? activeMembership.getJoinedAt() : now,
                    userId
            );
            return requireActiveMembership(workspaceId, userId);
        }

        WorkspaceMemberEntity anyMembership = workspaceMemberEntityMapper.findAnyByWorkspaceIdAndUserId(workspaceId, userId);
        if (anyMembership != null) {
            LocalDateTime now = LocalDateTime.now();
            workspaceMemberEntityMapper.restoreMembership(
                    anyMembership.getId(),
                    WorkspaceMemberRole.OWNER.name(),
                    WorkspaceMemberStatus.ACTIVE.name(),
                    userId,
                    anyMembership.getJoinedAt() != null ? anyMembership.getJoinedAt() : now,
                    userId
            );
            return requireActiveMembership(workspaceId, userId);
        }

        try {
            return insertOwnerMembership(workspaceId, userId);
        } catch (DuplicateKeyException exception) {
            if (!isWorkspaceMemberUniqueConflict(exception)) {
                throw exception;
            }
            return requireActiveMembership(workspaceId, userId);
        }
    }

    private WorkspaceMemberEntity insertOwnerMembership(Long workspaceId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        WorkspaceMemberEntity ownerMember = WorkspaceMemberEntity.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .memberRole(WorkspaceMemberRole.OWNER.name())
                .memberStatus(WorkspaceMemberStatus.ACTIVE.name())
                .invitedBy(userId)
                .joinedAt(now)
                .build();
        ownerMember.setCreatedBy(userId);
        ownerMember.setUpdatedBy(userId);
        ownerMember.setCreatedAt(now);
        ownerMember.setUpdatedAt(now);
        ownerMember.setIsDeleted(0);
        workspaceMemberEntityMapper.insertWorkspaceMember(ownerMember);
        return requireActiveMembership(workspaceId, userId);
    }

    private WorkspaceMemberEntity requireActiveMembership(Long workspaceId, Long userId) {
        WorkspaceMemberEntity membership = workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (membership == null || !WorkspaceMemberStatus.ACTIVE.name().equals(membership.getMemberStatus())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "工作空间成员关系修复失败");
        }
        return membership;
    }

    private boolean isDefaultWorkspaceOwnerNameConflict(DuplicateKeyException exception) {
        return containsConstraintName(exception, DEFAULT_WORKSPACE_OWNER_NAME_INDEX);
    }

    private boolean isWorkspaceMemberUniqueConflict(DuplicateKeyException exception) {
        return containsConstraintName(exception, WORKSPACE_MEMBER_UNIQUE_INDEX);
    }

    private boolean containsConstraintName(DuplicateKeyException exception, String constraintName) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause != null ? cause.getMessage() : exception.getMessage();
        return message != null && message.contains(constraintName);
    }

    private void validatePageRequest(PageRequest pageRequest) {
        if (pageRequest.getPageNo() <= 0 || pageRequest.getPageSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNo/pageSize 非法");
        }
    }
}
