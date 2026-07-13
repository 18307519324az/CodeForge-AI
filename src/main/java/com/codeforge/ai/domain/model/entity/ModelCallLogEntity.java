package com.codeforge.ai.domain.model.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ModelCallLogEntity implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    // Legacy fields (original table columns)
    private Long id;
    private Long taskId;
    private Long appId;
    private Long sessionId;
    private Long providerId;
    private String modelName;
    private String requestId;
    private String status;       // callStatus maps to DB 'status'
    private Integer inputTokens; // promptTokens maps to DB 'input_tokens'
    private Integer outputTokens;// completionTokens maps to DB 'output_tokens'
    private Long durationMs;     // latencyMs maps to DB 'duration_ms'
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    // Extended fields
    private String providerCode;
    private String apiProtocol;
    private Boolean fallbackUsed;
    private String generationSource;
    private Long promptTemplateVersionId;
    private String promptTemplateCode;
    private Integer promptTemplateVersionNo;
    private String systemPromptSha256;
    private String userPromptSha256;
    private String combinedPromptFingerprint;
    private Long latencyMs;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
}
