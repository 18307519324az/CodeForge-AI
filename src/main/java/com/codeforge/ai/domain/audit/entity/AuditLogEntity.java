package com.codeforge.ai.domain.audit.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("audit_log")
public class AuditLogEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long workspaceId;
    private Long actorUserId;
    private String actionCode;
    private String targetType;
    private String targetId;
    private String requestId;
    private String detailJson;
    private String ipAddress;
    private LocalDateTime createdAt;
}
