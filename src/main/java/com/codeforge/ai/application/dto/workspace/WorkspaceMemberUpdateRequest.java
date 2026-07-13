package com.codeforge.ai.application.dto.workspace;

import jakarta.validation.constraints.NotBlank;

public class WorkspaceMemberUpdateRequest {

    @NotBlank(message = "memberRole 不能为空")
    private String memberRole;

    public String getMemberRole() {
        return memberRole;
    }

    public void setMemberRole(String memberRole) {
        this.memberRole = memberRole;
    }
}
