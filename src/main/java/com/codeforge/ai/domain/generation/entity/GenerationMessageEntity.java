package com.codeforge.ai.domain.generation.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationMessageEntity implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private Long id;
    private Long workspaceId;
    private Long appId;
    private Long taskId;
    private Long userId;
    private String messageRole;
    private String messageContent;
    private String messageType;
    private LocalDateTime createdAt;
    private Integer isDeleted;
}
