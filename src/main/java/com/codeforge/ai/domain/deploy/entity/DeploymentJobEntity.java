package com.codeforge.ai.domain.deploy.entity;

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
@Table("deployment_job")
public class DeploymentJobEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long appId;
    private Long appVersionId;
    private String environmentCode;
    private String deployTarget;
    private String deployStatus;
    private String runtimeConfigJson;
    private String requestId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
