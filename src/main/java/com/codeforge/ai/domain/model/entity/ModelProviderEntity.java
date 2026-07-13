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

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("model_provider")
public class ModelProviderEntity implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @Id
    private Long id;
    private String providerCode;
    private String providerName;
    private String baseUrl;
    private String authMode;
    private String apiProtocol;
    private String secretRef;
    private String apiKeyEnv;
    private String credentialSource;
    private String defaultModel;
    private Integer priority;
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
