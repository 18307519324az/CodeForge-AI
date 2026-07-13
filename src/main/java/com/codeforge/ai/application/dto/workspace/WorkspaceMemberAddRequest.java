package com.codeforge.ai.application.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class WorkspaceMemberAddRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "memberRole 不能为空")
    private String memberRole;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMemberRole() {
        return memberRole;
    }

    public void setMemberRole(String memberRole) {
        this.memberRole = memberRole;
    }
}
