package com.codeforge.ai.domain.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
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
@Table("ai_routing_config")
public class AiRoutingConfigEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;
    private String routingMode;
    private String pinnedProviderCode;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
