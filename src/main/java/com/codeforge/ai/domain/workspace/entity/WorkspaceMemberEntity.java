package com.codeforge.ai.domain.workspace.entity;

import com.codeforge.ai.domain.common.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("workspace_member")
public class WorkspaceMemberEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long workspaceId;
    private Long userId;
    private String memberRole;
    private String memberStatus;
    private Long invitedBy;
    private LocalDateTime joinedAt;
}
