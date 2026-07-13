package com.codeforge.ai.domain.prompt.entity;

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
@Table("prompt_template_version")
public class PromptTemplateVersionEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long templateId;
    private Integer versionNo;
    private String systemPrompt;
    private String userPrompt;
    private String variablesJson;
    private String modelStrategyJson;
    private String status;
    private Long publishedBy;
    private LocalDateTime publishedAt;

    public boolean isCanonicallyPublished() {
        return com.codeforge.ai.domain.prompt.enums.PromptTemplateVersionStatus.PUBLISHED.name().equals(status);
    }

    public boolean hasLegacyPublishedMarker() {
        return publishedAt != null;
    }

    public boolean isEffectivelyPublished() {
        return isCanonicallyPublished() || hasLegacyPublishedMarker();
    }

    /** @deprecated use {@link #isEffectivelyPublished()} for read compatibility */
    @Deprecated
    public boolean isPublished() {
        return isEffectivelyPublished();
    }
}
