package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.workspace.WorkspaceCreateRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceDetailResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberAddRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceMemberUpdateRequest;
import com.codeforge.ai.application.dto.workspace.WorkspaceSummaryResponse;
import com.codeforge.ai.application.dto.workspace.WorkspaceUpdateRequest;
import com.codeforge.ai.application.service.WorkspaceApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.request.PageRequest;
import com.codeforge.ai.shared.response.ApiResponse;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.response.ResultUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/workspaces")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceController {

    private final WorkspaceApplicationService workspaceApplicationService;

    @PostMapping
    public ApiResponse<WorkspaceDetailResponse> createWorkspace(@AuthenticationPrincipal CurrentUser currentUser,
                                                                @Valid @RequestBody WorkspaceCreateRequest request) {
        return ResultUtils.success(workspaceApplicationService.createWorkspace(currentUser, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<WorkspaceSummaryResponse>> listWorkspaces(@AuthenticationPrincipal CurrentUser currentUser,
                                                                              @ModelAttribute PageRequest pageRequest) {
        return ResultUtils.success(workspaceApplicationService.listWorkspaces(currentUser, pageRequest));
    }

    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceDetailResponse> getWorkspace(@AuthenticationPrincipal CurrentUser currentUser,
                                                             @PathVariable Long workspaceId) {
        return ResultUtils.success(workspaceApplicationService.getWorkspace(currentUser, workspaceId));
    }

    @PutMapping("/{workspaceId}")
    public ApiResponse<WorkspaceDetailResponse> updateWorkspace(@AuthenticationPrincipal CurrentUser currentUser,
                                                                @PathVariable Long workspaceId,
                                                                @Valid @RequestBody WorkspaceUpdateRequest request) {
        return ResultUtils.success(workspaceApplicationService.updateWorkspace(currentUser, workspaceId, request));
    }

    @GetMapping("/{workspaceId}/members")
    public ApiResponse<List<WorkspaceMemberResponse>> listMembers(@AuthenticationPrincipal CurrentUser currentUser,
                                                                  @PathVariable Long workspaceId) {
        return ResultUtils.success(workspaceApplicationService.listMembers(currentUser, workspaceId));
    }

    @PostMapping("/{workspaceId}/members")
    public ApiResponse<WorkspaceMemberResponse> addMember(@AuthenticationPrincipal CurrentUser currentUser,
                                                          @PathVariable Long workspaceId,
                                                          @Valid @RequestBody WorkspaceMemberAddRequest request) {
        return ResultUtils.success(workspaceApplicationService.addMember(currentUser, workspaceId, request));
    }

    @PutMapping("/{workspaceId}/members/{memberId}")
    public ApiResponse<WorkspaceMemberResponse> updateMember(@AuthenticationPrincipal CurrentUser currentUser,
                                                             @PathVariable Long workspaceId,
                                                             @PathVariable Long memberId,
                                                             @Valid @RequestBody WorkspaceMemberUpdateRequest request) {
        return ResultUtils.success(workspaceApplicationService.updateMember(currentUser, workspaceId, memberId, request));
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ApiResponse<Void> removeMember(@AuthenticationPrincipal CurrentUser currentUser,
                                          @PathVariable Long workspaceId,
                                          @PathVariable Long memberId) {
        workspaceApplicationService.removeMember(currentUser, workspaceId, memberId);
        return ResultUtils.success(null);
    }

    @PostMapping("/default")
    public ApiResponse<WorkspaceDetailResponse> getOrCreateDefaultWorkspace(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResultUtils.success(workspaceApplicationService.getOrCreateDefaultWorkspace(currentUser));
    }
}
